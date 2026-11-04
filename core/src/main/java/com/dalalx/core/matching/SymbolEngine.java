package com.dalalx.core.matching;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.book.LevelDetail;
import com.dalalx.core.breaker.MarketState;
import com.dalalx.core.breaker.TradingSession;
import com.dalalx.core.book.OrderBook;
import com.dalalx.core.events.BookChanged;
import com.dalalx.core.events.EventBus;
import com.dalalx.core.events.MarketStatusChanged;
import com.dalalx.core.events.OrderAccepted;
import com.dalalx.core.events.OrderCancelled;
import com.dalalx.core.events.OrderRejected;
import com.dalalx.core.events.OrderView;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.Trade;
import com.dalalx.core.xray.XRayRecorder;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p><b>Trading phases (State pattern):</b> before matching, the engine asks
 * its {@link TradingSession} whether orders are allowed right now. While the
 * circuit breaker has trading halted, new orders are rejected with the reason
 * (cancels still work). After matching, every trade is shown to the breaker;
 * if one trips it, a {@link MarketStatusChanged} event follows the usual ones.
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

    /** How many levels of the opposite side an X-Ray captures. */
    static final int XRAY_DEPTH = 10;

    /** The reason on an {@code OrderCancelled} event when the trader asked for the cancel. */
    public static final String CANCELLED_BY_TRADER = "cancelled by trader";

    private final String symbol;
    private final OrderBook book;
    private final OrderFactory factory;
    private final MatchingStrategies strategies;
    private final Map<Long, Order> recentOrders;
    private final EventBus events;
    private final Clock clock;
    private final TradingSession session;
    private XRayRecorder xrays = XRayRecorder.NONE;
    private OrderGate gate = OrderGate.OPEN;

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
        this(symbol, factory, strategies, events, clock, TradingSession.alwaysOpen(symbol), DEFAULT_HISTORY);
    }

    /**
     * @param session decides when trading is allowed (circuit breaker, auctions)
     */
    public SymbolEngine(String symbol, OrderFactory factory, MatchingStrategies strategies,
                        EventBus events, Clock clock, TradingSession session) {
        this(symbol, factory, strategies, events, clock, session, DEFAULT_HISTORY);
    }

    SymbolEngine(String symbol, OrderFactory factory, MatchingStrategies strategies, int historySize) {
        this(symbol, factory, strategies, new EventBus(), Clock.systemUTC(), TradingSession.alwaysOpen(symbol),
                historySize);
    }

    private SymbolEngine(String symbol, OrderFactory factory, MatchingStrategies strategies,
                         EventBus events, Clock clock, TradingSession session, int historySize) {
        this.book = new OrderBook(symbol);
        this.symbol = symbol;
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
        this.strategies = Objects.requireNonNull(strategies, "strategies must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.session = Objects.requireNonNull(session, "session must not be null");
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
        tick();
        Order order = factory.create(request);
        recentOrders.put(order.id(), order);
        Optional<String> refusal = gate.refusal(request).or(session::refusal);
        if (refusal.isPresent()) {
            MatchResult refused = refuse(order, refusal.get());
            if (xrays.wants(request)) {
                xrays.record(OrderView.of(order), List.of(), refused, clock.instant());
            }
            return refused;
        }
        OrderView arrived = OrderView.of(order); // frozen before matching changes it
        boolean xray = xrays.wants(request);
        List<LevelDetail> before = xray ? book.detail(order.side().opposite(), XRAY_DEPTH) : List.of();
        MatchResult result = strategies.forType(order.type()).match(order, book);
        publishOutcome(arrived, result);
        if (xray) {
            xrays.record(arrived, before, result, clock.instant());
        }
        watchForBreaker(result.trades());
        return result;
    }

    /**
     * Adds a check every order must pass (e.g. surveillance suspensions). Like
     * {@link #recordXRays}, call it before the worker thread starts.
     */
    public void guardWith(OrderGate newGate) {
        this.gate = Objects.requireNonNull(newGate, "gate must not be null");
    }

    /**
     * Cancels every resting order of one trader (used when surveillance
     * suspends them). Each cancel is announced as usual.
     *
     * @return how many orders were cancelled
     */
    public int cancelAllFor(String traderId) {
        List<Long> ids = book.orderIdsOf(traderId);
        ids.forEach(this::cancel);
        return ids.size();
    }

    /**
     * Starts recording X-Rays for the orders {@code recorder} wants. Must be
     * called before the worker thread starts (the exchange does this), so the
     * thread start publishes it safely.
     */
    public void recordXRays(XRayRecorder recorder) {
        this.xrays = Objects.requireNonNull(recorder, "recorder must not be null");
    }

    /** Rejects an order the current phase doesn't allow (e.g. during a halt). */
    private MatchResult refuse(Order order, String reason) {
        order.reject();
        MatchResult result = new MatchResult(order, List.of(), false, reason);
        events.publish(new OrderRejected(symbol, clock.instant(), events.nextSequence(), OrderView.of(order), reason));
        return result;
    }

    /** Shows each trade to the circuit breaker; announces a halt if one trips it. */
    private void watchForBreaker(List<Trade> trades) {
        for (Trade trade : trades) {
            Optional<MarketState> halted = session.onTrade(trade);
            if (halted.isPresent()) {
                publishStatus(halted.get());
                return;
            }
        }
    }

    /**
     * Lets time pass for this stock: a halt whose time is up ends. Called before
     * every order and, by the exchange, a few times a second.
     */
    public void tick() {
        session.onClock(clock.instant()).ifPresent(this::publishStatus);
    }

    private void publishStatus(MarketState state) {
        events.publish(new MarketStatusChanged(symbol, clock.instant(), events.nextSequence(), state.phase(),
                state.reason().orElse(null), state.until().orElse(null)));
    }

    /** @return the trading session (phase and breaker), for use on the owning thread */
    public TradingSession session() {
        return session;
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
