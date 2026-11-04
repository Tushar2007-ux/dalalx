package com.dalalx.core.matching;

import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.Trade;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 * @param reason   why the order was rejected or its remainder cancelled, or
 *                 {@code null} if nothing needs explaining
 */
public record MatchResult(Order incoming, List<Trade> trades, boolean rested, String reason) {

    public MatchResult {
        Objects.requireNonNull(incoming, "incoming must not be null");
        trades = List.copyOf(trades);
    }

    /** A result that needs no explanation (filled, or rested in the book). */
    public MatchResult(Order incoming, List<Trade> trades, boolean rested) {
        this(incoming, trades, rested, null);
    }

    /** @return total shares the incoming order traded */
    public long filledQty() {
        return trades.stream().mapToLong(Trade::qty).sum();
    }

    /** @return the incoming order's final status */
    public OrderStatus status() {
        return incoming.status();
    }

    /** @return the explanation, if the order was rejected or partly cancelled */
    public Optional<String> explanation() {
        return Optional.ofNullable(reason);
    }
}
