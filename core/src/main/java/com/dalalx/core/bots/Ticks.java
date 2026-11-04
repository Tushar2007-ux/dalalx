package com.dalalx.core.bots;

/**
 * Price arithmetic for bots, all in whole paise.
 *
 * <p>Indian exchanges quote in ticks of ₹0.05 (5 paise), so every price a bot
 * sends is rounded to a multiple of {@link #TICK}. Percentages are applied in
 * basis points (1 bp = 0.01%) with integer maths, so no {@code double} ever
 * touches money.
 */
public final class Ticks {

    /** The minimum price step: ₹0.05. */
    public static final long TICK = 5;

    private Ticks() {
    }

    /** @return {@code price} rounded to the nearest tick, never below one tick */
    public static long round(long price) {
        long rounded = Math.floorDiv(price + TICK / 2, TICK) * TICK; // integer rounding, half up
        return Math.max(TICK, rounded);
    }

    /** @return {@code price} moved by {@code basisPoints} (100 bp = 1%), rounded to a tick */
    public static long moveBy(long price, long basisPoints) {
        return round(price + price * basisPoints / 10_000);
    }

    /** @return the change from {@code from} to {@code to} in basis points */
    public static long changeBp(long from, long to) {
        return from == 0 ? 0 : (to - from) * 10_000 / from;
    }
}
