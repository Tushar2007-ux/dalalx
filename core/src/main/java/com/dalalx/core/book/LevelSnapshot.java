package com.dalalx.core.book;

/**
 * Immutable picture of one price level, for display and tests.
 *
 * <p>A {@code record} (value object), so it can be handed to other threads,
 * such as the server sending book depth to the browser, without exposing the
 * live, non-thread-safe {@link PriceLevel}.
 *
 * @param price      price in paise
 * @param totalQty   sum of the remaining quantity of every order at this price
 * @param orderCount how many orders are queued at this price
 */
public record LevelSnapshot(long price, long totalQty, int orderCount) {
}
