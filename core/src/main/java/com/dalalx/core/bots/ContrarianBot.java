package com.dalalx.core.bots;

import com.dalalx.core.model.Side;
import java.util.List;

/**
 * A hireable correspondent that does the opposite of the Momentum Chaser:
 * it buys after a fall and sells after a rise ("buy the fear, sell the
 * cheer"), which tends to calm the market down. Same interface, opposite
 * strategy: a neat demonstration that the runner doesn't care which
 * {@link TradingBot} it drives.
 */
public final class ContrarianBot extends AbstractBot {

    static final int LOOKBACK = 10;
    static final long MOVE_BP = 10;

    public ContrarianBot(String id, String name) {
        super(id, name, "Contrarian", "Buys the fear, sells the cheer.", 2);
    }

    @Override
    public void act(BotContext ctx) {
        String symbol = ctx.pick(ctx.market().symbols());
        List<Long> prices = ctx.market().recentPrices(symbol, LOOKBACK);
        if (prices.size() < 5) {
            return;
        }
        long change = Ticks.changeBp(prices.get(0), prices.get(prices.size() - 1));
        long last = ctx.market().lastPrice(symbol);
        if (change < -MOVE_BP) {
            ioc(ctx, symbol, Side.BUY, Ticks.moveBy(last, 15), ctx.qty(20, 150, 10));
        } else if (change > MOVE_BP) {
            ioc(ctx, symbol, Side.SELL, Ticks.moveBy(last, -15), ctx.qty(20, 150, 10));
        }
    }
}
