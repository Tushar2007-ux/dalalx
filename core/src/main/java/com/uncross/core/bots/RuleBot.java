package com.uncross.core.bots;

import com.uncross.core.matching.MatchResult;
import com.uncross.core.model.Side;
import java.util.Objects;

/**
 * A bot a reader builds: it follows one {@link BotRecipe} and nothing else.
 *
 * <p>Each turn it compares the price with the price when it last acted (its
 * <em>anchor</em>). If the recipe's trigger fires, it sends an
 * immediate-or-cancel order a little through the price, so it trades now or
 * not at all, then sets a new anchor. It keeps count of what it holds and
 * will not go past the recipe's limit, long or short.
 *
 * <p>It is an {@link AbstractBot} like the five regulars, so the runner, the
 * ledger, the leaderboard, surveillance and the tournament all treat it the
 * same way. That is the point of programming to the {@link TradingBot}
 * interface: a new kind of trader slots in without anything else changing.
 */
public final class RuleBot extends AbstractBot {

    /** How far through the last price its orders reach, so they actually trade. */
    static final long REACH_BP = 50;

    private final BotRecipe recipe;
    private final int ticksPerSecond;
    private long anchor;
    private int ticksSince;
    private long holding;

    public RuleBot(BotRecipe recipe, int ticksPerSecond) {
        super(recipe.id(), recipe.name(), "Your Bot", recipe.describe(), 1);
        this.recipe = Objects.requireNonNull(recipe, "recipe must not be null");
        if (ticksPerSecond < 1) {
            throw new IllegalArgumentException("ticksPerSecond must be >= 1");
        }
        this.ticksPerSecond = ticksPerSecond;
    }

    @Override
    public void act(BotContext ctx) {
        if (!ctx.market().symbols().contains(recipe.symbol())) {
            return; // its stock is not (or no longer) listed
        }
        long last = ctx.market().lastPrice(recipe.symbol());
        if (anchor == 0) {
            anchor = last; // the first look just sets the starting point
            return;
        }
        ticksSince++;
        if (!recipe.when().fires(anchor, last, ticksSince, recipe.amount(), ticksPerSecond)) {
            return;
        }
        trade(ctx, last);
        anchor = last;
        ticksSince = 0;
    }

    private void trade(BotContext ctx, long last) {
        long qty = room();
        if (qty <= 0) {
            return; // at its limit: the rule fired, but the bot stays put
        }
        boolean buy = recipe.side() == Side.BUY;
        long price = Ticks.moveBy(last, buy ? REACH_BP : -REACH_BP);
        MatchResult result = ioc(ctx, recipe.symbol(), recipe.side(), price, qty);
        holding += buy ? result.filledQty() : -result.filledQty();
    }

    /** How many shares it may still trade in its direction without passing its limit. */
    long room() {
        long headroom = recipe.side() == Side.BUY ? recipe.maxShares() - holding : recipe.maxShares() + holding;
        return Math.min(recipe.qty(), headroom);
    }

    public BotRecipe recipe() {
        return recipe;
    }

    /** Net shares bought by this bot's own orders (negative if it has sold more than it bought). */
    public long holding() {
        return holding;
    }
}
