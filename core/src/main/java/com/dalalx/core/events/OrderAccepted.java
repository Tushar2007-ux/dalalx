package com.dalalx.core.events;

import java.time.Instant;

/**
 * The exchange accepted a new order and is about to match it.
 *
 * @param symbol    stock symbol
 * @param timestamp when it was accepted
 * @param sequence  global event number
 * @param order     the order as it arrived
 */
public record OrderAccepted(String symbol, Instant timestamp, long sequence, OrderView order)
        implements EngineEvent {
}
