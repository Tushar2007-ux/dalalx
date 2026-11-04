package com.dalalx.core.matching;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.book.OrderBook;
import com.dalalx.core.events.BookChanged;
import com.dalalx.core.events.EventBus;
import com.dalalx.core.events.OrderAccepted;
import com.dalalx.core.events.OrderCancelled;
import com.dalalx.core.events.OrderRejected;
import com.dalalx.core.events.OrderView;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.Trade;
import java.time.Clock;
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
 * <p><b>Events (Observer pattern):</b> everything the engine does is announced
 * on an {@link EventBus}, always in this order for one command:
 * {@code OrderAccepted → TradeExecuted × n → OrderCancelled/OrderRejected (if
 * any) → BookChanged (if the book changed)}. The engine doesn't know who is
 * listening (screen, surveillance, headlines); it just publishes.
 *
 * <p><b>Thread confinement:</b> not thread-safe. In the multi-stock exchange,
 * exactly one worker thread owns each {@code SymbolEngine}; all other threads
 * reach it through that worker's queue.
 */
public final class SymbolEngine implements MatchingEngine {

    /** How many recent orders to remember for cancel replies. */
    static final int DEFAULT_HISTORY = 50_000;

    /** How many price levels per side a {@link BookChanged} event carries. */
    public static final int EVENT_DEPTH = 10;

    static final String CANCELLED_BY_TRADER = "cancelled by trader";

    private final String symbol;
    private final OrderBook book;
    private final OrderFactory factory;
    private final MatchingStrategies strategies;
    private final Map<Long, Order> recentOrders;
    private final EventBus events;
    private final Clock clock;

    /**
     * An engine whose events go nowhere (handy in tests that only check results).
     *
     * @param symbol     the stock this engine trades
     * @param factory    creates orders with ids and sequence numbers
     * @param strategies the matcher for each order type
     */
    public SymbolEngine(String symbol, OrderFactory factory, MatchingStrategies strategies) {
        this(symbol, factory, strategies, new EventBus(), Clock.systemUTC());
    }

    /**
     * @param symbol     the stock this engine trades
     * @param factory    creates orders with ids and sequence numbers
     * @param strategies the matcher for each order type
     * @param events     where to publish what happens
     * @param clock      source of event timestamps
     */
    public SymbolEngine(String symbol, OrderFactory factory, MatchingStrategies strategies,
                        EventBus events, Clock clock) {
        this(symbol, factory, strategies, events, clock, DEFAULT_HISTORY);
    }

    SymbolEngine(String symbol, OrderFactory factory, MatchingStrategies strategies, int historySize) {
        this(symbol, factory, strategies, new EventBus(), Clock.systemUTC(), historySize);
    }

    private SymbolEngine(String symbol, OrderFactory factory, MatchingStrategies strategies,
                         EventBus events, Clock clock, int historySize) {
        this.book = new OrderBook(symbol);
        this.symbol = symbol;
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
        this.strategies = Objects.requireNonNull(strategies, "strategies must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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
        OrderView arrived = OrderView.of(order); // frozen before matching changes it
        MatchResult result = strategies.forType(order.type()).match(order, book);
        publishOutcome(arrived, result);
        return result;
    }

    /** Announces one submit, in the documented event order. */
    private void publishOutcome(OrderView arrived, MatchResult result) {
        if (result.status() == OrderStatus.REJECTED) {
            events.publish(new OrderRejected(symbol, clock.instant(), events.nextSequence(),
                    OrderView.of(result.incoming()), result.reason()));
            return;
        }
        events.publish(new OrderAccepted(symbol, clock.instant(), events.nextSequence(), arrived));
        for (Trade trade : result.trades()) {
            events.publish(new TradeExecuted(symbol, trade.timestamp(), events.nextSequence(), trade));
        }
        if (result.status() == OrderStatus.CANCELLED) {
            publishCancelled(result.incoming(), result.reason());
        }
        if (!result.trades().isEmpty() || result.rested()) {
            publishBook();
        }
    }

    private void publishCancelled(Order order, String reason) {
        events.publish(new OrderCancelled(symbol, clock.instant(), events.nextSequence(),
                OrderView.of(order), reason));
    }

    private void publishBook() {
        events.publish(new BookChanged(symbol, clock.instant(), events.nextSequence(),
                book.snapshot(EVENT_DEPTH)));
    }

    @Override
    public CancelResult cancel(long orderId) {
        Optional<Order> resting = book.remove(orderId);
        if (resting.isPresent()) {
            Order order = resting.get();
            order.cancel();
            publishCancelled(order, CANCELLED_BY_TRADER);
            publishBook();
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
