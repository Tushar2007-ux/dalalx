package com.dalalx.core.book;

import java.util.List;

/**
 * A price level with every order in its queue, oldest (first to fill) first.
 * Used by X-Ray to show exactly whose orders an incoming order will meet.
 *
 * @param price  the level's price in paise
 * @param orders the queue, in time priority
 */
public record LevelDetail(long price, List<RestingOrder> orders) {

    public LevelDetail {
        orders = List.copyOf(orders);
    }

    /** @return shares waiting at this price */
    public long totalQty() {
        return orders.stream().mapToLong(RestingOrder::remainingQty).sum();
    }
}
