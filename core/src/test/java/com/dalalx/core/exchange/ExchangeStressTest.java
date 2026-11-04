package com.dalalx.core.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.events.EventBus;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Hammers the exchange from 8 threads at once and then checks that nothing
 * was lost, duplicated or corrupted.
 *
 * <p>If the order book were touched by two threads at the same time, this test
 * would typically fail: a lost update would break quantity conservation, or a
 * corrupted TreeMap would leave the book crossed or throw.
 */
class ExchangeStressTest {

    private static final int PRODUCERS = 8;
    private static final int ORDERS_PER_PRODUCER = 50_000 / PRODUCERS;
    private static final List<String> SYMBOLS = Exchange.DEFAULT_STOCKS.stream().map(ListedStock::symbol).toList();

    @Test
    void fiftyThousandOrdersFromEightThreadsKeepEveryInvariant() throws Exception {
        EventBus bus = new EventBus();
        Queue<Trade> trades = new ConcurrentLinkedQueue<>();
        bus.register(e -> {
            if (e instanceof TradeExecuted t) {
                trades.add(t.trade());
            }
        });
        Exchange exchange = new Exchange(Exchange.DEFAULT_STOCKS, bus, Clock.systemUTC());
        exchange.start();

        ExecutorService producers = Executors.newFixedThreadPool(PRODUCERS);
        List<Future<List<MatchResult>>> work = new ArrayList<>();
        for (int p = 0; p < PRODUCERS; p++) {
            long seed = 1_000 + p;
            work.add(producers.submit(() -> produce(exchange, new Random(seed))));
        }
        List<MatchResult> results = new ArrayList<>();
        for (Future<List<MatchResult>> f : work) {
            results.addAll(f.get(60, TimeUnit.SECONDS)); // every future completed
        }
        producers.shutdown();
        List<BookSnapshot> books = SYMBOLS.stream().map(s -> exchange.snapshot(s, 1_000).join()).toList();
        assertThat(exchange.shutdown(10, TimeUnit.SECONDS)).isTrue();
        // After shutdown the worker threads have ended (join), so reading the
        // orders here is safe: Thread.join gives a happens-before edge.

        assertThat(results).hasSize(PRODUCERS * ORDERS_PER_PRODUCER);
        assertNoLostOrDuplicatedOrders(results);
        assertQuantitiesConserved(results, trades);
        assertThat(books).allSatisfy(ExchangeStressTest::assertNotCrossed);
    }

    /** One producer thread: random orders across all symbols, plus some cancels. */
    private static List<MatchResult> produce(Exchange exchange, Random random) {
        List<CompletableFuture<MatchResult>> futures = new ArrayList<>();
        List<Long> myIds = new ArrayList<>();
        for (int i = 0; i < ORDERS_PER_PRODUCER; i++) {
            String symbol = SYMBOLS.get(random.nextInt(SYMBOLS.size()));
            long mid = exchange.stock(symbol).orElseThrow().referencePrice();
            OrderType type = random.nextInt(10) < 8 ? OrderType.LIMIT
                    : random.nextBoolean() ? OrderType.MARKET : OrderType.IOC;
            long price = mid + (random.nextInt(41) - 20) * 50L; // within ±₹10 of the reference
            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            futures.add(exchange.submit(new OrderRequest(symbol, "P" + random.nextInt(50), side, type,
                    type == OrderType.MARKET ? 0 : price, 1 + random.nextInt(200))));
            if (random.nextInt(10) == 0 && !myIds.isEmpty()) {
                exchange.cancel(myIds.get(random.nextInt(myIds.size())));
            }
            if (i % 100 == 0) { // learn some of our ids so we can cancel them
                myIds.add(futures.getLast().join().incoming().id());
            }
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private static void assertNoLostOrDuplicatedOrders(List<MatchResult> results) {
        Set<Long> ids = new HashSet<>();
        for (MatchResult r : results) {
            assertThat(ids.add(r.incoming().id())).as("duplicate order id %d", r.incoming().id()).isTrue();
        }
    }

    private static void assertQuantitiesConserved(List<MatchResult> results, Queue<Trade> trades) {
        Map<Long, Long> tradedPerOrder = new HashMap<>();
        for (Trade t : trades) {
            tradedPerOrder.merge(t.buyOrderId(), t.qty(), Long::sum);
            tradedPerOrder.merge(t.sellOrderId(), t.qty(), Long::sum);
        }
        for (MatchResult r : results) {
            Order o = r.incoming();
            assertThat(o.filledQty() + o.remainingQty()).isEqualTo(o.originalQty());
            assertThat(tradedPerOrder.getOrDefault(o.id(), 0L))
                    .as("trades for order %d", o.id()).isEqualTo(o.filledQty());
        }
    }

    private static void assertNotCrossed(BookSnapshot book) {
        if (!book.bids().isEmpty() && !book.asks().isEmpty()) {
            assertThat(book.bids().getFirst().price())
                    .as("%s best bid < best ask", book.symbol())
                    .isLessThan(book.asks().getFirst().price());
        }
    }
}
