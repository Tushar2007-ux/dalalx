package com.dalalx.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * An order the exchange has accepted, with its changing fill state.
 *
 * <p>{@code Order} is an <em>entity</em>: it has an identity ({@link #id()}) and
 * its state changes over time as trades fill it. That's why it's a class and
 * not a record, and why {@link #equals(Object)} compares ids only. Two orders
 * with the same id are the same order, even if one copy is more filled.
 *
 * <p><b>Encapsulation:</b> there are no setters. The only way to change an order
 * is through {@link #fill(long)}, {@link #cancel()} and {@link #reject()}. Each
 * checks that the change is a legal move in the {@link OrderStatus} lifecycle
 * and throws {@link IllegalStateException} if it isn't. That way no outside code
 * can put an order into an impossible state, such as "FILLED with 10 shares
 * remaining".
 *
 * <p><b>Thread confinement:</b> this class is deliberately <em>not</em>
 * thread-safe. Each symbol's order book, and every order in it, is owned by a
 * single worker thread, and all access goes through that worker's queue. Only
 * one thread ever touches an {@code Order}, so it needs no locks.
 */
public final class Order {

    private final long id;
    private final long sequence;
    private final Instant timestamp;
    private final String symbol;
    private final String traderId;
    private final Side side;
    private final OrderType type;
    private final long limitPrice;
    private final long originalQty;

    // The only mutable fields. They change only through fill/cancel/reject.
    private long remainingQty;
    private OrderStatus status;

    /**
     * Creates a NEW order from an already-validated request.
     *
     * @param id        unique order id assigned by the exchange
     * @param sequence  arrival number; a lower number arrived earlier and wins
     *                  time priority at the same price
     * @param timestamp when the exchange accepted the order
     * @param request   the validated trader request
     */
    public Order(long id, long sequence, Instant timestamp, OrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0 but was " + sequence);
        }
        this.id = id;
        this.sequence = sequence;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.symbol = request.symbol();
        this.traderId = request.traderId();
        this.side = request.side();
        this.type = request.type();
        this.limitPrice = request.price();
        this.originalQty = request.qty();
        this.remainingQty = request.qty();
        this.status = OrderStatus.NEW;
    }

    /**
     * Records that {@code qty} shares of this order have traded.
     * Moves to PARTIALLY_FILLED, or to FILLED when nothing remains.
     *
     * @param qty shares traded; must be &gt; 0 and &lt;= {@link #remainingQty()}
     * @throws IllegalStateException    if the order is already terminal or REJECTED
     * @throws IllegalArgumentException if {@code qty} is not positive or exceeds what remains
     */
    public void fill(long qty) {
        requireActive("fill");
        if (qty <= 0 || qty > remainingQty) {
            throw new IllegalArgumentException(
                    "fill qty must be in 1.." + remainingQty + " but was " + qty);
        }
        remainingQty -= qty;
        status = remainingQty == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    /**
     * Cancels whatever hasn't been filled yet. Legal from NEW or PARTIALLY_FILLED.
     * {@link #remainingQty()} is left unchanged so it still shows how many shares
     * were cancelled, and {@link #filledQty()} stays correct.
     *
     * @throws IllegalStateException if the order is already FILLED, CANCELLED or REJECTED
     */
    public void cancel() {
        requireActive("cancel");
        status = OrderStatus.CANCELLED;
    }

    /**
     * Rejects the order before it has traded at all (for example, because the
     * market is halted). Legal only from NEW: once shares have traded, the order
     * can't be un-traded, so a part-filled order must be cancelled instead.
     *
     * @throws IllegalStateException if the order is not NEW
     */
    public void reject() {
        if (status != OrderStatus.NEW) {
            throw illegalTransition("reject");
        }
        status = OrderStatus.REJECTED;
    }

    private void requireActive(String action) {
        if (status.isTerminal()) {
            throw illegalTransition(action);
        }
    }

    private IllegalStateException illegalTransition(String action) {
        return new IllegalStateException("cannot " + action + " order " + id + " in status " + status);
    }

    /** @return {@code true} while the order can still trade (NEW or PARTIALLY_FILLED) */
    public boolean isActive() {
        return !status.isTerminal();
    }

    /** @return shares traded so far */
    public long filledQty() {
        return originalQty - remainingQty;
    }

    public long id() {
        return id;
    }

    public long sequence() {
        return sequence;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String symbol() {
        return symbol;
    }

    public String traderId() {
        return traderId;
    }

    public Side side() {
        return side;
    }

    public OrderType type() {
        return type;
    }

    /** @return limit price in paise; always {@code 0} for MARKET orders */
    public long limitPrice() {
        return limitPrice;
    }

    public long originalQty() {
        return originalQty;
    }

    public long remainingQty() {
        return remainingQty;
    }

    public OrderStatus status() {
        return status;
    }

    /** Entity equality: two {@code Order}s are equal when they have the same id. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Order that && this.id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Order[id=%d, %s %s %s %d/%d @ %s, %s]".formatted(id, symbol, side, type,
                remainingQty, originalQty, Money.format(limitPrice), status);
    }
}
