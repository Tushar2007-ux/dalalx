package com.dalalx.core.bots;

import com.dalalx.core.model.Side;

/**
 * A hireable correspondent that trades small amounts at random, on a random
 * side, close to the last price. It keeps the tape busy without pushing the
 * price anywhere in particular, like the many small retail orders in a real
 * market.
 */
public final class NoiseBot extends AbstractBot {

    public NoiseBot(String id, String name) {
        super(id, name, "Noise Trader", "Trades at random to keep the tape busy.", 2);
    }

    @Override
    public void act(BotContext ctx) {
        String symbol = ctx.pick(ctx.market().symbols());
        Side side = ctx.random().nextBoolean() ? Side.BUY : Side.SELL;
        long last = ctx.market().lastPrice(symbol);
        ioc(ctx, symbol, side, Ticks.moveBy(last, side == Side.BUY ? 8 : -8), ctx.qty(10, 80, 10));
    }
}
