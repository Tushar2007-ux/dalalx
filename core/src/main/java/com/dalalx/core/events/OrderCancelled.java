package com.dalalx.core.events;

import java.time.Instant;

/**
 * An order (or what was left of it) was cancelled: either the trader asked, or
 * an IOC/market remainder could not fill immediately.
 *
 * @param symbol    stock symbol
 * @param timestamp when it was cancelled
 * @param sequence  global event number
 * @param order     the order after cancelling; {@code remainingQty} is how much was cancelled
 * @param reason    why, in plain English
 */
public record OrderCancelled(String symbol, Instant timestamp, long sequence, OrderView order, String reason)
        implements EngineEvent {
}
