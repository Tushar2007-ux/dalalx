package com.dalalx.core.exchange;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.events.EventBus;
import com.dalalx.core.matching.CancelResult;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.matching.MatchingStrategies;
import com.dalalx.core.matching.OrderFactory;
import com.dalalx.core.matching.SymbolEngine;
import com.dalalx.core.model.OrderRequest;
import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * The whole stock exchange: one {@link SymbolWorker} per listed stock, all
 * sharing one id generator and one {@link EventBus}.
 *
 * <p>This is the entry point the rest of the system uses (the server, bots,
 * scenarios). Every method returns a {@link CompletableFuture}, because the
 * real work happens later on the symbol's own worker thread. The caller can
 * {@code join()} to wait, or attach a callback.
 *
 * <p><b>Thread safety:</b> this class is safe to call from any number of
 * threads at once. It doesn't use locks around order books: it only routes
 * each call to the right worker's queue (see {@link SymbolWorker}). The only
 * shared state is:
 * <ul>
 *   <li>one {@link AtomicLong} for order ids, sequence numbers and trade ids
 *       (lock-free and never hands out the same number twice);</li>
 *   <li>a small map from order id to symbol, so {@link #cancel(long)} needs only
 *       the id. It is size-bounded and synchronised, and is touched once per
 *       order, never during matching.</li>
 * </ul>
 */
public final class Exchange {

    /** The five simulated stocks and their starting prices. */
    public static final List<ListedStock> DEFAULT_STOCKS = List.of(
            new ListedStock("RELIANCE", "Reliance Industries (simulated)", 246000),
            new ListedStock("TCS", "Tata Consultancy Services (simulated)", 409000),
            new ListedStock("INFY", "Infosys (simulated)", 154200),
            new ListedStock("HDFCBANK", "HDFC Bank (simulated)", 168300),
            new ListedStock("TATAMOTORS", "Tata Motors (simulated)", 94235));

    private static final int ROUTE_HISTORY = 500_000;

    private final Map<String, ListedStock> stocks = new LinkedHashMap<>();
    private final Map<String, SymbolWorker> workers = new LinkedHashMap<>();
    private final Map<Long, String> symbolOfOrder;
    private final EventBus events;
    private final AtomicLong ids = new AtomicLong();

    /** An exchange for {@link #DEFAULT_STOCKS} using the system clock. */
    public Exchange(EventBus events) {
        this(DEFAULT_STOCKS, events, Clock.systemUTC());
    }

    /**
     * @param listed the stocks to trade
     * @param events where every symbol publishes its events
     * @param clock  source of timestamps
     */
    public Exchange(List<ListedStock> listed, EventBus events, Clock clock) {
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.symbolOfOrder = Collections.synchronizedMap(boundedMap(ROUTE_HISTORY));
        // One counter for order ids, sequences and trade ids: all unique and increasing.
        // The factory and matchers hold no mutable state of their own (only this
        // AtomicLong and the clock), so all workers can safely share them.
        OrderFactory factory = new OrderFactory(ids::incrementAndGet, ids::incrementAndGet, clock);
        MatchingStrategies strategies = MatchingStrategies.standard(ids::incrementAndGet, clock);
        for (ListedStock stock : listed) {
            stocks.put(stock.symbol(), stock);
            workers.put(stock.symbol(),
                    new SymbolWorker(new SymbolEngine(stock.symbol(), factory, strategies, events, clock)));
        }
    }

    private static Map<Long, String> boundedMap(int maxSize) {
        return new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
                return size() > maxSize;
            }
        };
    }

    /** Starts every symbol's worker thread. */
    public void start() {
        workers.values().forEach(SymbolWorker::start);
    }

    /**
     * Places an order. It is matched on its symbol's worker thread.
     *
     * @return the match result, once the worker has processed it
     * @throws IllegalArgumentException if the symbol isn't listed
     */
    public CompletableFuture<MatchResult> submit(OrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return workerFor(request.symbol()).enqueue("submit", engine -> {
            MatchResult result = engine.submit(request);
            symbolOfOrder.put(result.incoming().id(), request.symbol());
            return result;
        });
    }

    /**
     * Cancels an order by id alone. The exchange looks up which symbol it
     * belongs to and routes the cancel to that worker.
     */
    public CompletableFuture<CancelResult> cancel(long orderId) {
        String symbol = symbolOfOrder.get(orderId);
        if (symbol == null) {
            return CompletableFuture.completedFuture(CancelResult.unknown(orderId));
        }
        return workerFor(symbol).enqueue("cancel", engine -> engine.cancel(orderId));
    }

    /** @return an immutable view of the top {@code depth} levels of one book */
    public CompletableFuture<BookSnapshot> snapshot(String symbol, int depth) {
        return workerFor(symbol).enqueue("snapshot", engine -> engine.snapshot(depth));
    }

    /**
     * Runs any action on a symbol's worker thread. Later features (circuit
     * breaker ticks, call auctions, bot market views) use this so that they too
     * only touch a book from its own thread.
     */
    public <T> CompletableFuture<T> onWorker(String symbol, String name, Function<SymbolEngine, T> action) {
        return workerFor(symbol).enqueue(name, action);
    }

    private SymbolWorker workerFor(String symbol) {
        SymbolWorker worker = workers.get(symbol);
        if (worker == null) {
            throw new IllegalArgumentException("unknown symbol: " + symbol);
        }
        return worker;
    }

    /**
     * Stops accepting orders, lets queued commands finish, and waits for the
     * worker threads to end.
     *
     * @return {@code true} if every worker stopped within the timeout
     */
    public boolean shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        workers.values().forEach(SymbolWorker::shutdown);
        boolean allStopped = true;
        for (SymbolWorker worker : workers.values()) {
            allStopped &= worker.awaitTermination(timeout, unit);
        }
        return allStopped;
    }

    /** @return the listed stocks, in listing order */
    public List<ListedStock> stocks() {
        return List.copyOf(stocks.values());
    }

    /** @return the listed stock, if any */
    public Optional<ListedStock> stock(String symbol) {
        return Optional.ofNullable(stocks.get(symbol));
    }

    /** @return the event bus every symbol publishes to */
    public EventBus events() {
        return events;
    }
}
