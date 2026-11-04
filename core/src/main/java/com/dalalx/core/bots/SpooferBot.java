package com.dalalx.core.bots;

import com.dalalx.core.model.Side;

/**
 * <b>The Spoofer</b> — the villain, so that surveillance has someone to catch.
 *
 * <p>Its trick, repeated every few seconds: (1) stack three large bids on
 * neighbouring ticks just under the price, so the book looks full of eager
 * buyers; (2) on its very next turn, a quarter of a second later, sell a few
 * shares into the price those bids propped up, and pull all the bids before
 * anyone can trade with them. Big orders, never meant to fill, cancelled
 * within a second, at several stacked prices: textbook spoofing and layering,
 * exactly what {@code SpoofingRule} and {@code LayeringRule} look for.
 *
 * <p>It is a small state machine (idle → bids showing → resting) kept on the
 * bot thread like every bot's state.
 */
public final class SpooferBot extends AbstractBot {

    public static final String ID = "SPOOFER";

    static final int LEVELS = 3;
    /** How far under the last price the stack starts (0.10%). */
    static final long STACK_BP = -10;
    static final int REST_TICKS = 8;
    static final int START_CHANCE = 40;

    private enum Phase { IDLE, SHOWING, RESTING }

    private Phase phase = Phase.IDLE;
    private String symbol;
    private int restLeft;

    public SpooferBot() {
        super(ID, "The Spoofer", "Layering Bot", "Shows orders it never means to fill.", 1);
    }

    @Override
    public void act(BotContext ctx) {
        switch (phase) {
            case IDLE -> {
                if (ctx.chance(START_CHANCE)) {
                    showFakeBids(ctx);
                }
            }
            case SHOWING -> sellAndPull(ctx);
            case RESTING -> {
                if (--restLeft <= 0) {
                    phase = Phase.IDLE;
                }
            }
        }
    }

    private void showFakeBids(BotContext ctx) {
        symbol = ctx.pick(ctx.market().symbols());
        long top = Ticks.moveBy(ctx.market().lastPrice(symbol), STACK_BP);
        for (int i = 0; i < LEVELS; i++) {
            limit(ctx, symbol, Side.BUY, top - i * Ticks.TICK, ctx.qty(5_000, 11_000, 100));
        }
        phase = Phase.SHOWING;
    }

    private void sellAndPull(BotContext ctx) {
        ioc(ctx, symbol, Side.SELL, Ticks.moveBy(ctx.market().lastPrice(symbol), -5), ctx.qty(30, 80, 10));
        withdraw(ctx.orders(), symbol);
        phase = Phase.RESTING;
        restLeft = REST_TICKS;
    }
}
