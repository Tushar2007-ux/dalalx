package com.dalalx.core.model;

/**
 * Which side of the market an order is on: buying or selling.
 *
 * <p>An {@code enum} gives a closed, type-safe set of values, so a typo like
 * {@code "BYU"} can't get into the system the way it could with a {@code String}.
 * The enum also carries behaviour ({@link #opposite()}). This is basic
 * encapsulation: the knowledge that BUY and SELL are opposites lives with the
 * type, not scattered as {@code if} statements through the matching code.
 */
public enum Side {
    BUY,
    SELL;

    /**
     * Returns the side an order trades against. A BUY order matches against the
     * SELL side of the book, and the other way round.
     *
     * @return {@link #SELL} for BUY, {@link #BUY} for SELL
     */
    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}
