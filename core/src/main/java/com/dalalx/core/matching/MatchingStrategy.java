package com.dalalx.core.matching;

import com.dalalx.core.book.OrderBook;
import com.dalalx.core.model.Order;

/**
 * How one type of incoming order trades against an {@link OrderBook}.
 *
 * <p><b>Strategy pattern:</b> each order type (LIMIT, MARKET, IOC) has its own
 * implementation of this interface. The engine picks the strategy that matches
 * the order's type and calls {@link #match}, without needing an
 * {@code if/switch} on the type.
 *
 * <p><b>Open/Closed:</b> to add a new order type, write a new strategy class.
 * The engine and the existing strategies stay unchanged.
 */
public interface MatchingStrategy {

    /**
     * Matches {@code incoming} against {@code book}, filling both the incoming
     * order and the resting orders it trades with.
     *
     * @param incoming a new, active order for the book's symbol
     * @param book     the symbol's order book (owned by the calling worker thread)
     * @return the trades produced and what happened to the incoming order
     */
    MatchResult match(Order incoming, OrderBook book);
}
