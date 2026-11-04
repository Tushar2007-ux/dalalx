package com.dalalx.core.book;

import java.util.List;

/**
 * Immutable view of the top levels of an {@link OrderBook}.
 *
 * <p>Both lists are best-first: {@code bids} from highest price down,
 * {@code asks} from lowest price up. The compact constructor copies them with
 * {@link List#copyOf}, so the snapshot can't be changed after it's created,
 * even by whoever built it.
 *
 * @param symbol stock symbol
 * @param bids   buy levels, highest price first
 * @param asks   sell levels, lowest price first
 */
public record BookSnapshot(String symbol, List<LevelSnapshot> bids, List<LevelSnapshot> asks) {

    public BookSnapshot {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }
}
