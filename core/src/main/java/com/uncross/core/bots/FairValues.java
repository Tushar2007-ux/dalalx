package com.uncross.core.bots;

/**
 * Something that can move what the bots believe a stock is worth.
 *
 * <p>The news desk depends on this one-method interface rather than on the
 * whole {@link LiveMarketView}: it needs to nudge a value, nothing more.
 * That is the Interface Segregation principle, and it means a test can hand
 * the news desk a two-line fake.
 */
@FunctionalInterface
public interface FairValues {

    /** Moves the fair value of {@code symbol} by {@code basisPoints} (100 = 1%). */
    void shiftReference(String symbol, long basisPoints);
}
