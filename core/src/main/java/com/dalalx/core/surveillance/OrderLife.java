package com.dalalx.core.surveillance;

import com.dalalx.core.model.Side;
import java.time.Instant;

/**
 * One order's life as surveillance saw it: when it was placed, at what price
 * and size, and how and when it ended. Immutable: the log replaces the record
 * when the order fills or is cancelled.
 *
 * @param orderId  the order
 * @param traderId who placed it
 * @param symbol   the stock
 * @param side     BUY or SELL
 * @param price    limit price in paise (0 for market orders)
 * @param qty      shares asked for
 * @param placedAt when it was accepted
 * @param endedAt  when it filled or was cancelled, or {@code null} while live
 * @param outcome  what has happened to it so far
 * @param filled   shares traded
 */
public record OrderLife(long orderId, String traderId, String symbol, Side side, long price, long qty,
                        Instant placedAt, Instant endedAt, Outcome outcome, long filled) {

    /** Where an order is in its life. */
    public enum Outcome { LIVE, FILLED, CANCELLED }

    OrderLife fill(long more, Instant at) {
        long total = filled + more;
        return total >= qty
                ? new OrderLife(orderId, traderId, symbol, side, price, qty, placedAt, at, Outcome.FILLED, total)
                : new OrderLife(orderId, traderId, symbol, side, price, qty, placedAt, endedAt, outcome, total);
    }

    OrderLife cancel(Instant at) {
        return new OrderLife(orderId, traderId, symbol, side, price, qty, placedAt, at, Outcome.CANCELLED, filled);
    }

    /** @return how long the order lived before it was cancelled, in milliseconds (-1 if not cancelled) */
    public long lifeMillis() {
        return outcome == Outcome.CANCELLED ? endedAt.toEpochMilli() - placedAt.toEpochMilli() : -1;
    }
}
