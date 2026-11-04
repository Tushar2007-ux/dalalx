package com.dalalx.core.events;

import java.time.Instant;

/**
 * An order was refused without trading (for example a market order with no one
 * on the other side, or later an order sent while trading is halted).
 *
 * @param symbol    stock symbol
 * @param timestamp when it was rejected
 * @param sequence  global event number
 * @param order     the rejected order
 * @param reason    why, in plain English
 */
public record OrderRejected(String symbol, Instant timestamp, long sequence, OrderView order, String reason)
        implements EngineEvent {
}
