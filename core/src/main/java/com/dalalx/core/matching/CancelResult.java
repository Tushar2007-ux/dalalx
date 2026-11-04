package com.dalalx.core.matching;

import com.dalalx.core.model.Order;
import java.util.Optional;

/**
 * The outcome of a cancel request.
 *
 * <p>Cancelling can legitimately fail: the order may have filled a moment
 * before the cancel arrived (a classic race on real exchanges), may already be
 * cancelled, or may never have existed. Those aren't programming errors, so they
 * are reported as a value with a clear reason rather than thrown as exceptions.
 *
 * @param orderId   the id the caller asked to cancel
 * @param cancelled {@code true} if the order was resting and is now CANCELLED
 * @param order     the order, if the engine knows it ({@code null} if unknown)
 * @param reason    why the cancel failed, or {@code null} on success
 */
public record CancelResult(long orderId, boolean cancelled, Order order, String reason) {

    static final String UNKNOWN = "no such order";

    /** @return a successful cancel of {@code order} */
    public static CancelResult success(Order order) {
        return new CancelResult(order.id(), true, order, null);
    }

    /** @return a failed cancel for an order the engine has never seen */
    public static CancelResult unknown(long orderId) {
        return new CancelResult(orderId, false, null, UNKNOWN);
    }

    /** @return a failed cancel for an order that has already finished */
    public static CancelResult alreadyFinished(Order order) {
        return new CancelResult(order.id(), false, order, "order is already " + order.status());
    }

    /** @return the order, if the engine knows it */
    public Optional<Order> knownOrder() {
        return Optional.ofNullable(order);
    }
}
