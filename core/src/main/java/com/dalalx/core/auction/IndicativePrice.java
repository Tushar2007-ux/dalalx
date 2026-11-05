package com.dalalx.core.auction;

/**
 * Where a call auction would trade if it closed right now. Immutable.
 *
 * @param price   the single price everyone would trade at, in paise
 * @param volume  shares that would trade
 * @param buyQty  shares bid at that price or higher
 * @param sellQty shares offered at that price or lower
 */
public record IndicativePrice(long price, long volume, long buyQty, long sellQty) {

    /** @return buy interest minus sell interest at the price (positive: more buyers left over) */
    public long imbalance() {
        return buyQty - sellQty;
    }
}
