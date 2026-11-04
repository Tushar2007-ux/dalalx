package com.dalalx.core.bots;

import com.dalalx.core.model.Side;
import java.util.List;

/**
 * <b>Momentum Chaser</b> — the trend follower. It looks at the last few trades
 * of a stock: if the price has been rising it buys, if falling it sells, which
 * makes moves bigger (as real momentum traders do). With no clear trend it
 * sometimes makes a small random trade, playing the part of ordinary noise.
 *
 * <p>It uses IOC orders capped a little beyond the last price, so it takes
 * what is there without sweeping a thin book to absurd prices.
 */
public final class MomentumBot extends AbstractBot {

    public static final String ID = "MOMENTUM";

    static final int LOOKBACK = 10;
    /** A move bigger than this (0.08%) counts as a trend. */
    static final long TREND_BP = 8;
    /** How far past the last price it is willing to pay. */
    static final long REACH_BP = 20;
    static final int NOISE_CHANCE = 35;

    public MomentumBot() {
        this(ID, "Momentum Chaser", "Buys what is rising. Sells what is falling.");
    }

    /** Another trend follower hired from the Correspondents page. */
    public MomentumBot(String id, String name, String line) {
        super(id, name, "Trend Follower", line, 2);
    }

    @Override
    public void act(BotContext ctx) {
        String symbol = ctx.pick(ctx.market().symbols());
        Side side = decide(ctx, ctx.market().recentPrices(symbol, LOOKBACK));
        if (side == null) {
            return;
        }
        long last = ctx.market().lastPrice(symbol);
        long limit = Ticks.moveBy(last, side == Side.BUY ? REACH_BP : -REACH_BP);
        ioc(ctx, symbol, side, limit, ctx.qty(20, 150, 10));
    }

    /** @return BUY on an up-trend, SELL on a down-trend, sometimes a random side, else null */
    static Side decide(BotContext ctx, List<Long> prices) {
        if (prices.size() >= 5) {
            long change = Ticks.changeBp(prices.get(0), prices.get(prices.size() - 1));
            if (change > TREND_BP) {
                return Side.BUY;
            }
            if (change < -TREND_BP) {
                return Side.SELL;
            }
        }
        if (ctx.chance(NOISE_CHANCE)) {
            return ctx.random().nextBoolean() ? Side.BUY : Side.SELL;
        }
        return null;
    }
}
