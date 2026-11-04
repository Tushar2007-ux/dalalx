package com.dalalx.core.book;

import com.dalalx.core.model.Order;
import com.dalalx.core.model.Side;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;

/**
 * The limit order book for one symbol: every order waiting to trade, grouped
 * by side and price.
 *
 * <p><b>Data structures:</b>
 * <ul>
 *   <li>{@code bids}: a {@link TreeMap} with {@link Comparator#reverseOrder()},
 *       so {@code firstEntry()} is the <em>highest</em> bid (the best buyer).</li>
 *   <li>{@code asks}: a {@link TreeMap} in natural order, so
 *       {@code firstEntry()} is the <em>lowest</em> ask (the best seller).</li>
 *   <li>{@code ordersById}: a {@link HashMap} for O(1) lookup when cancelling.
 *       Without it, cancel would have to search every level.</li>
 * </ul>
 * Each map value is a {@link PriceLevel} (a FIFO queue), so the book as a whole
 * gives <em>price-time priority</em>: best price first, then earliest arrival.
 *
 * <p><b>Invariants this class protects:</b> empty price levels are removed
 * immediately; every resting order is active and in both the level and the
 * index; and the book is never <em>crossed</em> (best bid &lt; best ask).
 * {@link #add(Order)} refuses an order that would cross, because crossing
 * orders must be matched first.
 *
 * <p><b>Thread confinement:</b> this class is deliberately <em>not</em>
 * thread-safe and uses no locks. Each symbol has exactly one worker thread that
 * owns its book. Other threads never call it directly; they put commands on that
 * worker's {@code BlockingQueue}. Since only one thread ever touches the book,
 * there are no race conditions, and the code stays simple and fast.
 */
public final class OrderBook {

    private final String symbol;
    private final NavigableMap<Long, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Long, PriceLevel> asks = new TreeMap<>();
    private final Map<Long, Order> ordersById = new HashMap<>();

    public OrderBook(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        this.symbol = symbol;
    }

    /**
     * Rests an order in the book at the back of its price level.
     *
     * @throws IllegalArgumentException if the order is for another symbol, has no
     *         limit price (MARKET), is not active, is already in the book, or
     *         would cross the opposite side
     */
    public void add(Order order) {
        validateForResting(order);
        levelsFor(order.side())
                .computeIfAbsent(order.limitPrice(), PriceLevel::new)
                .add(order);
        ordersById.put(order.id(), order);
    }

    private void validateForResting(Order order) {
        if (!order.symbol().equals(symbol)) {
            throw new IllegalArgumentException("order is for " + order.symbol() + ", book is " + symbol);
        }
        if (order.limitPrice() <= 0) {
            throw new IllegalArgumentException("only orders with a limit price can rest: " + order);
        }
        if (!order.isActive()) {
            throw new IllegalArgumentException("cannot rest an inactive order: " + order);
        }
        if (ordersById.containsKey(order.id())) {
            throw new IllegalArgumentException("order " + order.id() + " is already in the book");
        }
        if (wouldCross(order)) {
            throw new IllegalArgumentException("order would cross the book; match it first: " + order);
        }
    }

    private boolean wouldCross(Order order) {
        return order.side() == Side.BUY
                ? bestAsk().isPresent() && order.limitPrice() >= bestAsk().getAsLong()
                : bestBid().isPresent() && order.limitPrice() <= bestBid().getAsLong();
    }

    /**
     * Takes an order out of the book, for example to cancel it. The order's
     * status is <em>not</em> changed here; the caller decides whether it's being
     * cancelled, amended, and so on.
     *
     * @return the removed order, or empty if no order with that id is resting
     */
    public Optional<Order> remove(long orderId) {
        Order order = ordersById.remove(orderId);
        if (order == null) {
            return Optional.empty();
        }
        NavigableMap<Long, PriceLevel> levels = levelsFor(order.side());
        PriceLevel level = levels.get(order.limitPrice());
        level.remove(order);
        removeIfEmpty(levels, level);
        return Optional.of(order);
    }

    /**
     * Returns the next order to trade on {@code side}: the oldest order at the
     * best price. Matching strategies use this to see who they'd trade against.
     */
    public Optional<Order> peekBest(Side side) {
        var best = levelsFor(side).firstEntry();
        return best == null ? Optional.empty() : Optional.of(best.getValue().peekOldest());
    }

    /**
     * Fills {@code qty} shares of the order returned by {@link #peekBest(Side)}.
     * If it becomes fully filled, it leaves the book; if its level becomes empty,
     * the level is removed.
     *
     * @return the resting order that was filled
     * @throws IllegalStateException if {@code side} has no orders
     */
    public Order fillBest(Side side, long qty) {
        NavigableMap<Long, PriceLevel> levels = levelsFor(side);
        var best = levels.firstEntry();
        if (best == null) {
            throw new IllegalStateException("no resting " + side + " orders in " + symbol);
        }
        PriceLevel level = best.getValue();
        Order filled = level.fillOldest(qty);
        if (!filled.isActive()) {
            ordersById.remove(filled.id());
        }
        removeIfEmpty(levels, level);
        return filled;
    }

    private static void removeIfEmpty(NavigableMap<Long, PriceLevel> levels, PriceLevel level) {
        if (level.isEmpty()) {
            levels.remove(level.price());
        }
    }

    /** @return highest resting buy price, or empty if there are no bids */
    public OptionalLong bestBid() {
        return bestPrice(bids);
    }

    /** @return lowest resting sell price, or empty if there are no asks */
    public OptionalLong bestAsk() {
        return bestPrice(asks);
    }

    private static OptionalLong bestPrice(NavigableMap<Long, PriceLevel> levels) {
        return levels.isEmpty() ? OptionalLong.empty() : OptionalLong.of(levels.firstKey());
    }

    /** @return best ask minus best bid in paise, or empty unless both sides have orders */
    public OptionalLong spread() {
        OptionalLong bid = bestBid();
        OptionalLong ask = bestAsk();
        return bid.isPresent() && ask.isPresent()
                ? OptionalLong.of(ask.getAsLong() - bid.getAsLong())
                : OptionalLong.empty();
    }

    /**
     * Returns an immutable view of up to {@code depth} best levels per side.
     *
     * @param depth maximum levels per side; must be &gt; 0
     */
    public BookSnapshot snapshot(int depth) {
        if (depth <= 0) {
            throw new IllegalArgumentException("depth must be > 0 but was " + depth);
        }
        return new BookSnapshot(symbol, topLevels(bids, depth), topLevels(asks, depth));
    }

    private static List<LevelSnapshot> topLevels(NavigableMap<Long, PriceLevel> levels, int depth) {
        return levels.values().stream().limit(depth).map(PriceLevel::toSnapshot).toList();
    }

    /**
     * Returns the best {@code depth} levels of one side with every order in
     * them, for X-Ray. Heavier than {@link #snapshot}, so only used on demand.
     *
     * @param side BUY for the bids, SELL for the asks
     */
    public List<LevelDetail> detail(Side side, int depth) {
        NavigableMap<Long, PriceLevel> levels = side == Side.BUY ? bids : asks;
        return levels.values().stream().limit(depth).map(PriceLevel::toDetail).toList();
    }

    /** @return the resting order with this id, if any (O(1)) */
    public Optional<Order> find(long orderId) {
        return Optional.ofNullable(ordersById.get(orderId));
    }

    /** @return number of resting orders on both sides */
    public int orderCount() {
        return ordersById.size();
    }

    public boolean isEmpty() {
        return ordersById.isEmpty();
    }

    public String symbol() {
        return symbol;
    }

    private NavigableMap<Long, PriceLevel> levelsFor(Side side) {
        return side == Side.BUY ? bids : asks;
    }
}
