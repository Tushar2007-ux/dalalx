package com.dalalx.core.matching;

import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderRequest;
import java.time.Clock;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Turns a validated {@link OrderRequest} into a live {@link Order}.
 *
 * <p><b>Factory pattern:</b> creating an {@code Order} needs things the caller
 * shouldn't have to know about: a unique id, an arrival sequence number (which
 * decides time priority) and the exchange's clock. Putting that in one factory
 * means:
 * <ul>
 *   <li>No other code calls {@code new Order(...)} with made-up ids, so ids and
 *       sequence numbers can't collide or go backwards.</li>
 *   <li>Id generation can be swapped (a shared {@code AtomicLong} in the
 *       multi-stock exchange, a simple counter in tests) without changing the
 *       code that places orders.</li>
 * </ul>
 *
 * <p>The id and sequence suppliers are separate on purpose: an id names an
 * order, while the sequence ranks arrivals. They happen to come from the same
 * counter in this project, but they mean different things.
 */
public final class OrderFactory {

    private final LongSupplier orderIds;
    private final LongSupplier sequences;
    private final Clock clock;

    /**
     * @param orderIds  source of unique order ids
     * @param sequences source of increasing arrival numbers
     * @param clock     source of acceptance timestamps
     */
    public OrderFactory(LongSupplier orderIds, LongSupplier sequences, Clock clock) {
        this.orderIds = Objects.requireNonNull(orderIds, "orderIds must not be null");
        this.sequences = Objects.requireNonNull(sequences, "sequences must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Creates a NEW order for an accepted request.
     *
     * @param request a request that has already passed validation
     * @return a new order with a fresh id and sequence number
     */
    public Order create(OrderRequest request) {
        return new Order(orderIds.getAsLong(), sequences.getAsLong(), clock.instant(), request);
    }
}
