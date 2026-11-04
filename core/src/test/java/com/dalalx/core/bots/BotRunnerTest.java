package com.dalalx.core.bots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BotRunnerTest {

    /** A bot that only records when it was asked to act or withdraw. */
    private static final class CountingBot extends AbstractBot {
        final List<String> calls = new ArrayList<>();
        private final boolean explode;

        CountingBot(String id, int every, boolean explode) {
            super(id, "test", every);
            this.explode = explode;
        }

        @Override
        public void act(BotContext ctx) {
            calls.add("act");
            if (explode) {
                throw new IllegalStateException("boom");
            }
        }
    }

    private final FakeMarket market = new FakeMarket().with("TCS", 400000);
    private final EngineGateway gateway = new EngineGateway();

    private BotRunner runner(TradingBot... bots) {
        return new BotRunner(List.of(bots), market, gateway, new ZeroRandom(), 100);
    }

    @Test
    void eachBotActsOnItsOwnSchedule() {
        CountingBot fast = new CountingBot("FAST", 1, false);
        CountingBot slow = new CountingBot("SLOW", 3, false);
        BotRunner runner = runner(fast, slow);

        for (int i = 0; i < 6; i++) {
            runner.tick();
        }

        assertThat(fast.calls).hasSize(6);
        assertThat(slow.calls).hasSize(2);
    }

    @Test
    void aFailingBotDoesNotStopTheOthers() {
        CountingBot bad = new CountingBot("BAD", 1, true);
        CountingBot good = new CountingBot("GOOD", 1, false);
        BotRunner runner = runner(bad, good);

        runner.tick();
        runner.tick();

        assertThat(good.calls).hasSize(2);
    }

    @Test
    void pausingStopsTradingAndWithdrawsRestingQuotesOnce() {
        MarketMakerBot munim = new MarketMakerBot();
        BotRunner runner = runner(munim);
        runner.tick();
        assertThat(gateway.engine("TCS").snapshot(5).bids()).isNotEmpty();

        runner.setStatus(MarketMakerBot.ID, BotStatus.PAUSED);
        runner.tick();
        runner.tick();

        assertThat(gateway.engine("TCS").snapshot(5).bids()).isEmpty();
        assertThat(gateway.cancelled).hasSize(10);          // withdrawn once, not every tick
        assertThat(runner.status(MarketMakerBot.ID)).contains(BotStatus.PAUSED);

        runner.setStatus(MarketMakerBot.ID, BotStatus.ACTIVE);
        runner.tick();
        assertThat(gateway.engine("TCS").snapshot(5).bids()).hasSize(5);
    }

    @Test
    void refusesDuplicateIdsAndUnknownBots() {
        assertThatIllegalArgumentException().isThrownBy(() -> runner(new MomentumBot(), new MomentumBot()));
        assertThat(runner(new MomentumBot()).setStatus("NOPE", BotStatus.PAUSED)).isFalse();
    }
}
