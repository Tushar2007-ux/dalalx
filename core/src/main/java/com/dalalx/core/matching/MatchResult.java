package com.dalalx.core.matching;

import com.dalalx.core.model.Order;
import com.dalalx.core.model.Trade;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of matching one incoming order.
 *
 * <p>A {@code record}, so a caller can't accidentally change the result. The
 * trade list is copied with {@link List#copyOf} and can't be modified.
 * {@code incoming} is the live {@link Order} entity, so its status and
 * remaining quantity show the final state after matching.
 *
 * @param incoming the order that was matched
 * @param trades   trades executed, in the order they happened (best price first)
 * @param rested   {@code true} if an unfilled remainder was added to the book
 */
public record MatchResult(Order incoming, List<Trade> trades, boolean rested) {

    public MatchResult {
        Objects.requireNonNull(incoming, "incoming must not be null");
        trades = List.copyOf(trades);
    }

    /** @return total shares the incoming order traded */
    public long filledQty() {
        return trades.stream().mapToLong(Trade::qty).sum();
    }
}
