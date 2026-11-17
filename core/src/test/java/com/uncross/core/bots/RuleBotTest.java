package com.uncross.core.bots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import org.junit.jupiter.api.Test;

/** A bot built from a menu: "when this happens, do that". */
class RuleBotTest {

    private final EngineGateway gateway = new EngineGateway();

    private BotContext at(long... prices) {
        return new BotContext(new FakeMarket().with("TCS", 400_000, prices), gateway, new ZeroRandom());
    }

    private static BotRecipe recipe(BotRecipe.When when, long amount, Side side, long qty, long max) {
        return new BotRecipe("Dip Buyer", "TCS", when, amount, side, qty, max);
    }

    @Test
    void itBuysWhenThePriceFallsFarEnoughFromWhereItStarted() {
        RuleBot bot = new RuleBot(recipe(BotRecipe.When.FALLS, 100, Side.BUY, 50, 500), 4);

        bot.act(at(400_000));   // first look: sets the starting point
        bot.act(at(397_000));   // down 0.75%: not yet
        assertThat(gateway.sent).isEmpty();

        bot.act(at(396_000));   // down 1.00%: buy
        assertThat(gateway.sent).singleElement().satisfies(o -> {
            assertThat(o.side()).isEqualTo(Side.BUY);
            assertThat(o.type()).isEqualTo(OrderType.IOC);
            assertThat(o.qty()).isEqualTo(50);
            assertThat(o.price()).isGreaterThan(396_000); // reaches through the price so it trades now
        });
    }

    @Test
    void afterActingItWaitsForANewMoveFromThere() {
        RuleBot bot = new RuleBot(recipe(BotRecipe.When.RISES, 100, Side.SELL, 10, 500), 4);
        bot.act(at(400_000));
        bot.act(at(404_000));
        bot.act(at(404_000));

        assertThat(gateway.sent).hasSize(1);
    }

    @Test
    void everyNSecondsCountsTurns() {
        RuleBot bot = new RuleBot(recipe(BotRecipe.When.EVERY, 2, Side.BUY, 10, 500), 4);
        for (int i = 0; i < 1 + 8 * 3; i++) {
            bot.act(at(400_000));
        }
        assertThat(gateway.sent).hasSize(3); // 2 s at 4 turns a second = every 8 turns
    }

    @Test
    void itNeverHoldsMoreThanItsLimit() {
        gateway.submit(new OrderRequest("TCS", "MM", Side.SELL, OrderType.LIMIT, 400_000, 1_000));
        gateway.sent.clear();
        RuleBot bot = new RuleBot(recipe(BotRecipe.When.EVERY, 1, Side.BUY, 60, 100), 1);

        for (int i = 0; i < 5; i++) {
            bot.act(at(400_000));
        }

        assertThat(bot.holding()).isEqualTo(100);
        assertThat(gateway.sent).extracting(OrderRequest::qty).containsExactly(60L, 40L);
    }

    @Test
    void theRecipeChecksItsNumbersAndReadsAsASentence() {
        BotRecipe r = recipe(BotRecipe.When.FALLS, 150, Side.BUY, 200, 1_000);
        assertThat(r.describe()).isEqualTo("Buys 200 TCS whenever the price falls 1.50%, holding at most 1000.");
        assertThat(r.id()).isEqualTo("DIPBUYER");

        assertThatIllegalArgumentException().isThrownBy(() -> recipe(BotRecipe.When.FALLS, 5, Side.BUY, 10, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> recipe(BotRecipe.When.EVERY, 0, Side.BUY, 10, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> recipe(BotRecipe.When.EVERY, 5, Side.BUY, 0, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> recipe(BotRecipe.When.EVERY, 5, Side.BUY, 50, 10));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BotRecipe("!!!", "TCS", BotRecipe.When.EVERY, 5, Side.BUY, 1, 1));
    }
}
