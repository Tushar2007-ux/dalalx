package com.uncross.core.matching;

import com.uncross.core.auction.CallAuction;
import com.uncross.core.auction.IndicativePrice;
import com.uncross.core.book.BookSnapshot;
import com.uncross.core.book.LevelDetail;
import com.uncross.core.book.LevelSnapshot;
import com.uncross.core.breaker.MarketPhase;
import com.uncross.core.breaker.MarketState;
import com.uncross.core.breaker.TradingSession;
import com.uncross.core.book.OrderBook;
import com.uncross.core.events.AuctionUpdate;
import com.uncross.core.events.BookChanged;
import com.uncross.core.events.EventBus;
import com.uncross.core.events.MarketStatusChanged;
import com.uncross.core.events.OrderAccepted;
import com.uncross.core.events.OrderCancelled;
import com.uncross.core.events.OrderReduced;
import com.uncross.core.events.OrderRejected;
import com.uncross.core.events.OrderView;
import com.uncross.core.events.TradeExecuted;
import com.uncross.core.model.Order;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderStatus;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Trade;
import com.uncross.core.xray.XRayRecorder;
import java.time.Clock;
import java.util.ArrayList;
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
 * <p><b>Call auction:</b> while the session is in an auction, orders only
 * join the book (it may be crossed on purpose) and an {@link AuctionUpdate}
 * with the indicative price follows every change. When the auction ends,
 * {@link CallAuction} matches everything that crosses at one price.
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

    /** Why the old order disappears when an amend becomes a cancel/replace. */
    public static final String REPLACED_BY_AMEND = "replaced by an amended order";

    /** Most stops that one price move may set off in a chain, so a cascade always ends. */
    static final int MAX_STOP_CHAIN = 1_000;

    private final String symbol;
    private final OrderBook book;
    private final StopBook stops = new StopBook();
    private final OrderFactory factory;
    private final MatchingStrategies strategies;
    private final Map<Long, Order> recentOrders;
    private final EventBus events;
    private final Clock clock;
    private final TradingSession session;
    private XRayRecorder xrays = XRayRecorder.NONE;
    /** Last traded price: the auction's tie-breaker. 0 until the first trade. */
    private long lastPrice;
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
        Optional<String> refusal = gate.refusal(request).or(() -> session.refusal(request.type()));
        if (refusal.isPresent()) {
            MatchResult refused = refuse(order, refusal.get());
            if (xrays.wants(request)) {
                xrays.record(OrderView.of(order), List.of(), refused, clock.instant());
            }
            return refused;
        }
        OrderView arrived = OrderView.of(order); // frozen before matching changes it
        if (order.type() == OrderType.STOP_LOSS) {
            return parkStop(order, arrived);
        }
        if (!session.state().matchesContinuously()) {
            return collectForAuction(order, arrived);
        }
        boolean xray = xrays.wants(request);
        List<LevelDetail> before = xray ? book.detail(order.side().opposite(), XRAY_DEPTH) : List.of();
        MatchResult result = strategies.forType(order.type()).match(order, book);
        publishOutcome(arrived, result);
        if (xray) {
            xrays.record(arrived, before, result, clock.instant());
        }
        watchForBreaker(result.trades());
        rememberLastPrice(result.trades());
        fireStops();
        return result;
    }

    /**
     * A stop-loss is accepted and set aside in the {@link StopBook}. If the
     * price is already past its trigger, it fires at once, the way a real
     * stop placed "through the market" does.
     */
    private MatchResult parkStop(Order stop, OrderView arrived) {
        events.publish(new OrderAccepted(symbol, clock.instant(), events.nextSequence(), arrived));
        stops.park(stop);
        fireStops();
        return new MatchResult(stop, List.of(), stop.isActive(), null);
    }

    /**
     * Fires every stop the last price has reached, as market orders. One
     * stop's trades can move the price far enough to set off the next, which
     * is exactly how a crash feeds itself; the loop follows that chain, but
     * stops the moment the circuit breaker halts the stock.
     */
    private void fireStops() {
        for (int fired = 0; fired < MAX_STOP_CHAIN && session.state().matchesContinuously(); ) {
            List<Order> triggered = stops.takeTriggered(lastPrice);
            if (triggered.isEmpty()) {
                return;
            }
            for (Order stop : triggered) {
                if (!session.state().matchesContinuously()) {
                    stops.park(stop); // halted mid-chain: it waits for the reopen
                    continue;
                }
                fire(stop);
                fired++;
            }
        }
    }

    private void fire(Order stop) {
        MatchResult result = strategies.forType(OrderType.STOP_LOSS).match(stop, book);
        for (Trade trade : result.trades()) {
            events.publish(new TradeExecuted(symbol, trade.timestamp(), events.nextSequence(), trade));
        }
        if (result.status() == OrderStatus.CANCELLED) {
            publishCancelled(stop, result.reason());
        }
        if (!result.trades().isEmpty()) {
            publishBook();
        }
        watchForBreaker(result.trades());
        rememberLastPrice(result.trades());
    }

    /** During a call auction an order only joins the book; nothing matches until the auction ends. */
    private MatchResult collectForAuction(Order order, OrderView arrived) {
        book.addForAuction(order);
        events.publish(new OrderAccepted(symbol, clock.instant(), events.nextSequence(), arrived));
        publishBook();
        publishAuction();
        return new MatchResult(order, List.of(), true, null);
    }

    /** Announces where the auction would trade if it closed now. */
    private void publishAuction() {
        Optional<IndicativePrice> price = CallAuction.indicative(book, lastPrice);
        BookSnapshot all = book.snapshot(Integer.MAX_VALUE);
        long bids = all.bids().stream().mapToLong(LevelSnapshot::totalQty).sum();
        long asks = all.asks().stream().mapToLong(LevelSnapshot::totalQty).sum();
        events.publish(new AuctionUpdate(symbol, clock.instant(), events.nextSequence(),
                price.map(IndicativePrice::price).orElse(null), price.map(IndicativePrice::volume).orElse(0L),
                price.map(IndicativePrice::buyQty).orElse(bids), price.map(IndicativePrice::sellQty).orElse(asks),
                session.state().until().orElse(null)));
    }

    /** Ends the auction: everything that crosses trades at the one equilibrium price. */
    private void uncross() {
        Optional<IndicativePrice> price = CallAuction.indicative(book, lastPrice);
        if (price.isEmpty()) {
            return;
        }
        List<Trade> trades = CallAuction.uncross(book, price.get().price(), factory::nextId, clock.instant());
        trades.forEach(t -> events.publish(new TradeExecuted(symbol, t.timestamp(), events.nextSequence(), t)));
        rememberLastPrice(trades);
        publishBook();
    }

    private void rememberLastPrice(List<Trade> trades) {
        if (!trades.isEmpty()) {
            lastPrice = trades.get(trades.size() - 1).price();
        }
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
        List<Long> ids = new ArrayList<>(book.orderIdsOf(traderId));
        ids.addAll(stops.orderIdsOf(traderId));
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
        MarketPhase before = session.phase();
        session.onClock(clock.instant()).ifPresent(next -> {
            if (before == MarketPhase.AUCTION) {
                uncross(); // the auction's trades come first, then "trading resumes"
            }
            publishStatus(next);
            if (next.phase() == MarketPhase.AUCTION) {
                publishAuction();
            }
            fireStops(); // a stop the auction price reached fires once trading resumes
        });
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
        Optional<Order> stop = stops.remove(orderId);
        if (stop.isPresent()) {
            stop.get().cancel();
            publishCancelled(stop.get(), CANCELLED_BY_TRADER);
            return CancelResult.success(stop.get());
        }
        Optional<Order> resting = book.remove(orderId);
        if (resting.isPresent()) {
            Order order = resting.get();
            order.cancel();
            publishCancelled(order, CANCELLED_BY_TRADER);
            publishBook();
            if (session.phase() == MarketPhase.AUCTION) {
                publishAuction();
            }
            return CancelResult.success(order);
        }
        Order known = recentOrders.get(orderId);
        return known == null ? CancelResult.unknown(orderId) : CancelResult.alreadyFinished(known);
    }

    /**
     * Changes the price or size of an order that is still waiting (in the book,
     * or as a stop). A smaller size at the same price is changed in place and
     * keeps its queue position; anything else is a cancel/replace. See
     * {@link AmendResult}.
     */
    public AmendResult amend(long orderId, long newPrice, long newQty) {
        Optional<Order> waiting = book.find(orderId).or(() -> stops.find(orderId));
        if (waiting.isEmpty()) {
            Order known = recentOrders.get(orderId);
            return known == null
                    ? AmendResult.refused(orderId, null, CancelResult.UNKNOWN)
                    : AmendResult.refused(orderId, known, "order is already " + known.status());
        }
        Order order = waiting.get();
        OrderRequest changed = order.toRequest().withPriceAndQty(newPrice, newQty); // validates the new figures
        if (newPrice == order.limitPrice() && newQty < order.remainingQty()) {
            return reduceInPlace(order, newQty);
        }
        removeForReplace(order);
        return AmendResult.replaced(orderId, submit(changed));
    }

    private AmendResult reduceInPlace(Order order, long newQty) {
        long before = order.remainingQty();
        if (order.type() == OrderType.STOP_LOSS) {
            order.reduceTo(newQty); // a waiting stop has no queue position in the book to protect
        } else {
            book.reduce(order.id(), newQty);
            publishBook();
            if (session.phase() == MarketPhase.AUCTION) {
                publishAuction();
            }
        }
        events.publish(new OrderReduced(symbol, clock.instant(), events.nextSequence(), OrderView.of(order), before));
        return AmendResult.inPlace(order);
    }

    private void removeForReplace(Order order) {
        stops.remove(order.id());
        book.remove(order.id());
        order.cancel();
        publishCancelled(order, REPLACED_BY_AMEND);
        if (order.type() != OrderType.STOP_LOSS) {
            publishBook();
        }
    }

    /** Stop-loss orders still waiting for their trigger, oldest first. */
    public List<Order> waitingStops() {
        return stops.waiting();
    }

    /** The last traded price, or 0 if nothing has traded yet. */
    public long lastPrice() {
        return lastPrice;
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
