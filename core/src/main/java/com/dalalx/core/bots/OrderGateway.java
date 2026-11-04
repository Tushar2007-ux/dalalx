package com.dalalx.core.bots;

import com.dalalx.core.matching.CancelResult;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.model.OrderRequest;

/**
 * How a bot sends orders: the same two actions a human trader has.
 *
 * <p>An interface, so unit tests can give a bot a recording fake instead of a
 * running exchange, and so bots never touch an order book directly. Bots go
 * through the front door like everyone else and get no special treatment.
 */
public interface OrderGateway {

    /** Sends an order and waits for the exchange's answer. */
    MatchResult submit(OrderRequest request);

    /** Cancels a resting order and waits for the answer. */
    CancelResult cancel(long orderId);
}
