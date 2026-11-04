package com.dalalx.core.matching;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.model.OrderRequest;

/**
 * The public face of the matching engine for one symbol: place an order,
 * cancel an order, look at the book.
 *
 * <p><b>Abstraction:</b> callers (the multi-stock exchange, tests, later the
 * server) depend on this interface, not on how matching is implemented. They
 * never see {@code OrderBook}, {@code PriceLevel} or the matchers. The
 * implementation can change, for example to record X-Ray traces or check a
 * circuit breaker, without any caller noticing.
 */
public interface MatchingEngine {

    /**
     * Accepts and matches a new order.
     *
     * @param request a validated request for this engine's symbol
     * @return the trades and what happened to the order
     */
    MatchResult submit(OrderRequest request);

    /**
     * Cancels a resting order.
     *
     * @param orderId the order to cancel
     * @return whether it worked and, if not, why
     */
    CancelResult cancel(long orderId);

    /**
     * @param depth maximum price levels per side
     * @return an immutable view of the top of the book
     */
    BookSnapshot snapshot(int depth);
}
