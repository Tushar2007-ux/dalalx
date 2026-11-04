package com.dalalx.core.bots;

/**
 * A simulated trader. Each kind of bot is a different implementation of this
 * one interface: the <b>Strategy pattern</b>. The {@link BotRunner} only ever
 * calls {@link #act}, and doesn't know or care whether it is driving the
 * market maker or the panic seller.
 */
public interface TradingBot {

    /** @return the trader id on this bot's orders and trades, e.g. {@code "MUNIM"} */
    String id();

    /** @return a short human description of the behaviour */
    String role();

    /** @return how often to act, in runner ticks (1 = every tick) */
    int everyTicks();

    /** Looks at the market and sends zero or more orders. Called on the bot thread only. */
    void act(BotContext context);

    /** Cancels everything this bot has resting (used when the bot is paused or stopped). */
    void withdraw(OrderGateway orders);
}
