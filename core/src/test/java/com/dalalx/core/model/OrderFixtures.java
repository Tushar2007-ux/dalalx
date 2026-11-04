package com.dalalx.core.model;

import java.time.Instant;

/**
 * Test helper that creates orders with increasing ids and sequence numbers,
 * so the order of calls in a test is the orders' arrival order.
 * Create one per test to start numbering from 1.
 */
public final class OrderFixtures {

    public static final String SYMBOL = "TCS";
    public static final Instant T0 = Instant.parse("2026-11-04T09:15:00Z");

    private long nextId = 1;

    public Order limit(Side side, long price, long qty) {
        return limit("T" + nextId, side, price, qty);
    }

    public Order limit(String traderId, Side side, long price, long qty) {
        return create(new OrderRequest(SYMBOL, traderId, side, OrderType.LIMIT, price, qty));
    }

    public Order of(OrderType type, Side side, long price, long qty) {
        return create(new OrderRequest(SYMBOL, "T" + nextId, side, type, price, qty));
    }

    private Order create(OrderRequest request) {
        long id = nextId++;
        return new Order(id, id, T0, request);
    }
}
