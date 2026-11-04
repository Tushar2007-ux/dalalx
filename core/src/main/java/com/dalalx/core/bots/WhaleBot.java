package com.dalalx.core.bots;

import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.model.Side;

/**
 * <b>The Whale</b> — the block trader. Now and then it decides to buy or sell
 * a large position (thousands of shares) in one stock. Dumping it all at once
 * would move the price against itself, so it works the order in small slices
 * over many turns, the way institutions do (a simple "parent/child order").
 *
 * <p>The parent order is the bot's own state; each turn sends one child IOC
 * slice and subtracts whatever filled.
 */
public final class WhaleBot extends AbstractBot {

    public static final String ID = "WHALE";

    static final int START_CHANCE = 25;
    /** Gives up on a parent order after this many slices. */
    static final int MAX_SLICES = 40;
    static final long REACH_BP = 10;

    /** The block currently being worked; {@code null} when idle. */
    public record Parent(String symbol, Side side, long remaining, int slicesLeft) {
    }

    private Parent parent;

    public WhaleBot() {
        super(ID, "The Whale", "Block Trader", "Moves size slowly. The book notices anyway.", 4);
    }

    @Override
    public void act(BotContext ctx) {
        if (parent == null) {
            if (ctx.chance(START_CHANCE)) {
                parent = new Parent(ctx.pick(ctx.market().symbols()),
                        ctx.random().nextBoolean() ? Side.BUY : Side.SELL, ctx.qty(2_000, 6_000, 100), MAX_SLICES);
            }
            return;
        }
        workSlice(ctx);
    }

    private void workSlice(BotContext ctx) {
        long last = ctx.market().lastPrice(parent.symbol());
        long limit = Ticks.moveBy(last, parent.side() == Side.BUY ? REACH_BP : -REACH_BP);
        long slice = Math.min(parent.remaining(), ctx.qty(100, 300, 50));
        MatchResult result = ioc(ctx, parent.symbol(), parent.side(), limit, slice);
        long remaining = parent.remaining() - result.filledQty();
        parent = remaining <= 0 || parent.slicesLeft() <= 1
                ? null
                : new Parent(parent.symbol(), parent.side(), remaining, parent.slicesLeft() - 1);
    }

    /** @return the block being worked, or {@code null} (for the UI and tests) */
    public Parent parent() {
        return parent;
    }
}
