package com.dalalx.core.matching;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.book.OrderBook;
import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@link MatchingEngine} for a single symbol.
 *
 * <p>It ties the pieces together: the {@link OrderFactory} creates the order,
 * {@link MatchingStrategies} picks the matcher for its type (Strategy pattern),
 * and the matcher trades it against this symbol's {@link OrderBook}. The engine
 * itself contains no {@code switch} on order type.
 *
 * <p><b>Remembering recent orders:</b> to give a helpful answer when someone
 * cancels an order that has <em>already</em> filled, the engine keeps the
 * most recent orders it has created in a bounded map. A {@link LinkedHashMap}
 * with {@code removeEldestEntry} forgets the oldest entry once the limit is
 * reached, so memory can't grow forever in a long-running market.
 *
 * <p><b>Thread confinement:</b> not thread-safe. In the multi-stock exchange,
 * exactly one worker thread owns each {@code SymbolEngine}; all other threads
 * reach it through that worker's queue.
 */
public final class SymbolEngine implements MatchingEngine {

    /** How many recent orders to remember for cancel replies. */
    static final int DEFAULT_HISTORY = 50_000;

    private final String symbol;
    private final OrderBook book;
    private final OrderFactory factory;
    private final MatchingStrategies strategies;
    private final Map<Long, Order> recentOrders;

    /**
     * @param symbol     the stock this engine trades
     * @param factory    creates orders with ids and sequence numbers
     * @param strategies the matcher for each order type
     */
    public SymbolEngine(String symbol, OrderFactory factory, MatchingStrategies strategies) {
        this(symbol, factory, strategies, DEFAULT_HISTORY);
    }

    SymbolEngine(String symbol, OrderFactory factory, MatchingStrategies strategies, int historySize) {
        this.book = new OrderBook(symbol);
        this.symbol = symbol;
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
        this.strategies = Objects.requireNonNull(strategies, "strategies must not be null");
        this.recentOrders = boundedMap(historySize);
    }

    private static Map<Long, Order> boundedMap(int maxSize) {
        return new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Order> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * @throws IllegalArgumentException if the request is for another symbol
     */
    @Override
    public MatchResult submit(OrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!request.symbol().equals(symbol)) {
            throw new IllegalArgumentException("engine for " + symbol + " got an order for " + request.symbol());
        }
        Order order = factory.create(request);
        recentOrders.put(order.id(), order);
        return strategies.forType(order.type()).match(order, book);
    }

    @Override
    public CancelResult cancel(long orderId) {
        Optional<Order> resting = book.remove(orderId);
        if (resting.isPresent()) {
            Order order = resting.get();
            order.cancel();
            return CancelResult.success(order);
        }
        Order known = recentOrders.get(orderId);
        return known == null ? CancelResult.unknown(orderId) : CancelResult.alreadyFinished(known);
    }

    @Override
    public BookSnapshot snapshot(int depth) {
        return book.snapshot(depth);
    }

    /** @return the symbol this engine trades */
    public String symbol() {
        return symbol;
    }

    /** @return the best bid/ask view of the book, for read-only use on the owning thread */
    OrderBook book() {
        return book;
    }
}
