package com.dalalx.core.book;

/**
 * One price level of the book, frozen at a moment (immutable).
 *
 * @param price      the level's price in paise
 * @param totalQty   shares waiting at this price
 * @param orderCount how many orders make up the level
 * @param mainTrader the trader holding the most shares at this level (shown in
 *                   X-Ray mode as the bot "behind" the level), or {@code null}
 */
public record LevelSnapshot(long price, long totalQty, int orderCount, String mainTrader) {

    /** A level without trader information (used by tests and simple callers). */
    public LevelSnapshot(long price, long totalQty, int orderCount) {
        this(price, totalQty, orderCount, null);
    }
}
