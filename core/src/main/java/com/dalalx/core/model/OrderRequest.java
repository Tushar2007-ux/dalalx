package com.dalalx.core.model;

import java.util.Objects;

/**
 * What a trader <em>asks</em> the exchange to do, before the exchange accepts it.
 *
 * <p>This is an immutable value object (a Java {@code record}). It's kept
 * separate from {@link Order}, which is the exchange's live, changing record of
 * an accepted order with an id, sequence number and status. Keeping the two
 * apart means an {@code Order} can only be built from input that has already
 * passed validation.
 *
 * <p>Validation happens in the compact constructor, so an invalid
 * {@code OrderRequest} can never exist ("fail fast"). For MARKET orders the
 * price is meaningless, so it's <em>normalised</em> to {@code 0} and any value
 * the client sent is ignored.
 *
 * @param symbol   stock symbol, e.g. {@code "TCS"}; must not be blank
 * @param traderId who is placing the order; must not be blank
 * @param side     BUY or SELL
 * @param type     LIMIT, MARKET or IOC
 * @param price    limit price in paise; must be &gt; 0 for LIMIT/IOC, ignored for MARKET
 * @param qty      number of shares; must be &gt; 0
 */
public record OrderRequest(String symbol, String traderId, Side side, OrderType type,
                           long price, long qty) {

    /** Validates every field and normalises the MARKET price. */
    /** Largest order accepted: 10 lakh shares (a "fat finger" guard). */
    public static final long MAX_QTY = 10_00_000;
    /** Highest limit price accepted: ₹10,00,000 a share. */
    public static final long MAX_PRICE = 10_00_000_00L;
    /** Longest trader id accepted. */
    public static final int MAX_TRADER_ID = 32;

    public OrderRequest {
        requireNotBlank(symbol, "symbol");
        requireNotBlank(traderId, "traderId");
        if (traderId.length() > MAX_TRADER_ID) {
            throw new IllegalArgumentException("traderId must be at most " + MAX_TRADER_ID + " characters");
        }
        Objects.requireNonNull(side, "side must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be > 0 but was " + qty);
        }
        if (qty > MAX_QTY) {
            throw new IllegalArgumentException("qty must be at most " + MAX_QTY + " but was " + qty);
        }
        if (price > MAX_PRICE) {
            throw new IllegalArgumentException("price must be at most " + MAX_PRICE + " paise but was " + price);
        }
        if (type.requiresLimitPrice() && price <= 0) {
            throw new IllegalArgumentException(type + " order needs price > 0 but was " + price);
        }
        if (!type.requiresLimitPrice()) {
            price = 0; // a compact constructor may reassign a parameter before it is stored
        }
    }

    private static void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
