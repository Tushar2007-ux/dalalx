package com.dalalx.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * An executed trade: {@code qty} shares of {@code symbol} changed hands at
 * {@code price}.
 *
 * <p>A trade is a fact that has already happened and can never change, so it's
 * an immutable {@code record} (a value object). Trades will be published to
 * observers (ticker, headlines, surveillance) through the Observer pattern.
 * Immutability means any number of threads can read the same {@code Trade}
 * safely.
 *
 * <p>{@code aggressorSide} is the side of the <em>incoming</em> order that caused
 * the trade by crossing the spread. The other order was already resting in the
 * book. Surveillance and the ticker use it to tell buying pressure from selling
 * pressure.
 *
 * @param id            unique trade id
 * @param symbol        stock symbol
 * @param price         execution price in paise; &gt; 0
 * @param qty           shares traded; &gt; 0
 * @param buyOrderId    id of the BUY order
 * @param sellOrderId   id of the SELL order
 * @param buyerId       trader id of the buyer
 * @param sellerId      trader id of the seller
 * @param aggressorSide side of the incoming order that triggered the trade
 * @param timestamp     when the trade executed
 */
public record Trade(long id, String symbol, long price, long qty,
                    long buyOrderId, long sellOrderId,
                    String buyerId, String sellerId,
                    Side aggressorSide, Instant timestamp) {

    /** Rejects impossible trades (non-positive price or qty, missing fields). */
    public Trade {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(buyerId, "buyerId must not be null");
        Objects.requireNonNull(sellerId, "sellerId must not be null");
        Objects.requireNonNull(aggressorSide, "aggressorSide must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (price <= 0) {
            throw new IllegalArgumentException("price must be > 0 but was " + price);
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be > 0 but was " + qty);
        }
        if (buyOrderId == sellOrderId) {
            throw new IllegalArgumentException("an order cannot trade with itself: " + buyOrderId);
        }
    }

    /** @return trade value in paise ({@code price × qty}) */
    public long notional() {
        return Math.multiplyExact(price, qty); // throws instead of silently overflowing
    }
}
