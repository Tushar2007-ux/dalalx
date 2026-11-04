package com.dalalx.core.book;

import com.dalalx.core.model.Order;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * All resting orders at one price on one side of the book, in arrival order.
 *
 * <p>This is where <em>time priority</em> lives. Orders join at the back of a
 * FIFO queue ({@link ArrayDeque}) and are filled from the front, so at the same
 * price the order that arrived first trades first. A partial fill leaves the
 * order at the front: it keeps its place in the queue.
 *
 * <p>The level also keeps a running {@link #totalQty()}, so the book can show
 * depth without adding up every order each time. To keep that total correct,
 * resting orders must only be filled through {@link #fillOldest(long)}, never
 * by calling {@link Order#fill(long)} directly.
 *
 * <p>The class is package-private: it is an internal detail of
 * {@link OrderBook} (encapsulation). Outside code sees only the immutable
 * {@link LevelSnapshot}. Like the book, it is not thread-safe; it is confined
 * to the symbol's worker thread.
 */
final class PriceLevel {

    private final long price;
    private final Deque<Order> queue = new ArrayDeque<>();
    private long totalQty;

    PriceLevel(long price) {
        this.price = price;
    }

    /** Adds an order at the back of the queue (it arrived last, so it trades last). */
    void add(Order order) {
        if (order.limitPrice() != price) {
            throw new IllegalArgumentException(
                    "order price " + order.limitPrice() + " does not match level " + price);
        }
        queue.addLast(order);
        totalQty += order.remainingQty();
    }

    /** @return the order at the front of the queue (next to trade), or {@code null} if empty */
    Order peekOldest() {
        return queue.peekFirst();
    }

    /**
     * Fills {@code qty} shares of the oldest order. If that order is now fully
     * filled, it leaves the queue; otherwise it stays at the front.
     *
     * @return the order that was filled
     */
    Order fillOldest(long qty) {
        Order oldest = queue.getFirst(); // throws NoSuchElementException if empty
        oldest.fill(qty);                // validates qty; throws before we touch totalQty
        totalQty -= qty;
        if (!oldest.isActive()) {
            queue.removeFirst();
        }
        return oldest;
    }

    /**
     * Removes a specific order, for example on cancel. This is O(n) in the
     * number of orders at this price, because {@code ArrayDeque} has to scan for
     * it. That's fine for this simulator, where levels are short.
     *
     * @return {@code true} if the order was in this level
     */
    boolean remove(Order order) {
        boolean removed = queue.remove(order);
        if (removed) {
            totalQty -= order.remainingQty();
        }
        return removed;
    }

    long price() {
        return price;
    }

    long totalQty() {
        return totalQty;
    }

    int orderCount() {
        return queue.size();
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    LevelSnapshot toSnapshot() {
        return new LevelSnapshot(price, totalQty, queue.size());
    }
}
