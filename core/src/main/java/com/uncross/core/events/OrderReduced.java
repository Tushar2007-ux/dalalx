package com.uncross.core.events;

import java.time.Instant;

/**
 * A waiting order was made smaller in place by an amend, keeping its place in
 * the queue. (An amend that changes the price, or adds shares, is reported as
 * an {@link OrderCancelled} followed by a new {@link OrderAccepted} instead.)
 *
 * <p>Listeners that hold something against an order, such as cash reserved
 * for it, use this to give back the difference.
 *
 * @param symbol            stock symbol
 * @param timestamp         when it was reduced
 * @param sequence          global event number
 * @param order             the order after the change
 * @param previousRemaining how many shares were waiting before
 */
public record OrderReduced(String symbol, Instant timestamp, long sequence, OrderView order, long previousRemaining)
        implements EngineEvent {
}
