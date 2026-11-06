package com.dalalx.core.bots;

import com.dalalx.core.model.Side;

/**
 * <b>Panic Seller</b> — the liquidity taker with weak nerves. Most of the time
 * it trades small. When it sees a stock dropping it dumps a large block well
 * below the last price, hitting every bid on the way. That is how a dip
 * becomes a slide (and, in Step 10, how the circuit breaker gets tripped).
 */
public final class PanicSellerBot extends AbstractBot {

    public static final String ID = "PANIC";

    static final int LOOKBACK = 20;
    /** A fall bigger than this (0.35%) over the last {@value #LOOKBACK} trades causes panic. */
    static final long PANIC_BP = 35;
    /** How far below the last price it is willing to sell when panicking (0.5%). */
    static final long DUMP_BP = 50;

    /** Turns it sits still after a dump (about 15 seconds), so one fall doesn't become a stampede on its own. */
    static final int COOLDOWN_TURNS = 20;

    private int cooldown;

    public PanicSellerBot() {
        super(ID, "Panic Seller", "Liquidity Taker", "Sells first, reads the news later.", 3);
    }

    @Override
    public void act(BotContext ctx) {
        String symbol = ctx.pick(ctx.market().symbols());
        long last = ctx.market().lastPrice(symbol);
        if (cooldown > 0) {
            cooldown--;
        } else if (isFalling(ctx.market(), symbol)) {
            ioc(ctx, symbol, Side.SELL, Ticks.moveBy(last, -DUMP_BP), ctx.qty(200, 800, 50));
            cooldown = COOLDOWN_TURNS;
        } else if (ctx.chance(12)) {
            ioc(ctx, symbol, Side.SELL, Ticks.moveBy(last, -10), ctx.qty(20, 100, 10));
        } else if (ctx.chance(14)) {
            // Buys back about as often as it sells on a calm day (12% then 14% of the rest ≈ equal),
            // so on its own it doesn't drag the market down; only a real fall sets it off.
            ioc(ctx, symbol, Side.BUY, Ticks.moveBy(last, 10), ctx.qty(20, 100, 10));
        }
    }

    static boolean isFalling(MarketView market, String symbol) {
        var prices = market.recentPrices(symbol, LOOKBACK);
        return prices.size() >= 8 && Ticks.changeBp(prices.get(0), prices.get(prices.size() - 1)) < -PANIC_BP;
    }
}
