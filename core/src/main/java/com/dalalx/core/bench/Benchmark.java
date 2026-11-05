package com.dalalx.core.bench;

import com.dalalx.core.events.EventBus;
import com.dalalx.core.exchange.Exchange;
import com.dalalx.core.exchange.ListedStock;
import com.dalalx.core.matching.MatchingStrategies;
import com.dalalx.core.matching.OrderFactory;
import com.dalalx.core.matching.SymbolEngine;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Measures how fast the engine matches, in two ways:
 *
 * <ol>
 *   <li><b>Engine alone:</b> one {@link SymbolEngine} on one thread, no queue,
 *       no listeners: the raw cost of matching an order against the book.</li>
 *   <li><b>Whole exchange:</b> several producer threads submitting to all five
 *       stocks through the workers' queues; each producer waits for its answer
 *       before sending the next, and each order is timed from {@code submit()}
 *       until its future completes: the round trip a client sees.</li>
 * </ol>
 *
 * <p>Orders are random but realistic: 70% limit orders within ±0.5% of the
 * mid, 15% IOC, 5% market, plus cancels of earlier orders, from a fixed seed
 * so every run sends the same flow. A warm-up round runs first so the JIT
 * compiler has optimised the hot code before anything is timed.
 *
 * <p>Run it with {@code ./bench.sh} (or {@code java -cp core/target/classes
 * com.dalalx.core.bench.Benchmark}). Results are machine-dependent; see
 * {@code docs/BENCHMARK.md}.
 */
public final class Benchmark {

    private static final String[] SYMBOLS = {"RELIANCE", "TCS", "INFY", "HDFCBANK", "TATAMOTORS"};
    private static final long MID = 100_000; // ₹1,000.00

    private Benchmark() {
    }

    public static void main(String[] args) throws Exception {
        int engineOrders = args.length > 0 ? Integer.parseInt(args[0]) : 1_000_000;
        int exchangeOrders = args.length > 1 ? Integer.parseInt(args[1]) : 400_000;
        int producers = args.length > 2 ? Integer.parseInt(args[2]) : 4;

        System.out.println("DalalX benchmark · Java " + Runtime.version() + " · "
                + Runtime.getRuntime().availableProcessors() + " CPUs");
        engineAlone(engineOrders / 5, false);            // warm-up
        Result engine = engineAlone(engineOrders, true);
        wholeExchange(exchangeOrders / 5, producers);    // warm-up
        Result exchange = wholeExchange(exchangeOrders, producers);

        System.out.println();
        System.out.println("| Test | Orders | Throughput (orders/s) | p50 | p99 | p99.9 |");
        System.out.println("|---|---:|---:|---:|---:|---:|");
        System.out.println(engine.row("Engine alone (1 thread, no queue)"));
        System.out.println(exchange.row("Whole exchange (" + producers + " producers → 5 workers)"));
    }

    /** One run's numbers. Latencies are in nanoseconds, sorted. */
    record Result(int orders, double seconds, long[] latencies) {
        double throughput() {
            return orders / seconds;
        }

        long percentile(double p) {
            int i = (int) Math.min(latencies.length - 1, Math.ceil(p / 100 * latencies.length) - 1);
            return latencies[Math.max(0, i)];
        }

        String row(String name) {
            return "| %s | %,d | %,.0f | %s | %s | %s |".formatted(name, orders, throughput(),
                    micros(percentile(50)), micros(percentile(99)), micros(percentile(99.9)));
        }

        private static String micros(long nanos) {
            return "%.1f µs".formatted(nanos / 1_000.0);
        }
    }

    /** A reproducible random order flow. */
    static List<OrderRequest> flow(int count, long seed, String symbol) {
        Random random = new Random(seed);
        List<OrderRequest> orders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            int roll = random.nextInt(100);
            OrderType type = roll < 80 ? OrderType.LIMIT : roll < 95 ? OrderType.IOC : OrderType.MARKET;
            long offset = (random.nextInt(101) - 50) * 10;          // ±₹5 in 10-paise steps
            long price = type == OrderType.MARKET ? 0 : MID + offset;
            orders.add(new OrderRequest(symbol, "B" + random.nextInt(50), side, type, price, 1 + random.nextInt(200)));
        }
        return orders;
    }

    static Result engineAlone(int count, boolean print) {
        AtomicLong ids = new AtomicLong();
        Clock clock = Clock.systemUTC();
        SymbolEngine engine = new SymbolEngine("TCS", new OrderFactory(ids::incrementAndGet, ids::incrementAndGet, clock),
                MatchingStrategies.standard(ids::incrementAndGet, clock), new EventBus(), clock);
        List<OrderRequest> orders = flow(count, 42, "TCS");
        Random cancels = new Random(7);
        long[] latency = new long[count];
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            long t0 = System.nanoTime();
            long id = engine.submit(orders.get(i)).incoming().id();
            if (cancels.nextInt(10) < 2) {
                engine.cancel(id - cancels.nextInt(40)); // some recent order, maybe already gone
            }
            latency[i] = System.nanoTime() - t0;
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        Arrays.sort(latency);
        if (print) {
            System.out.printf("engine alone: %,d orders in %.2f s%n", count, seconds);
        }
        return new Result(count, seconds, latency);
    }

    static Result wholeExchange(int count, int producers) throws InterruptedException {
        EventBus bus = new EventBus();
        List<ListedStock> stocks = Arrays.stream(SYMBOLS).map(s -> new ListedStock(s, s, MID)).toList();
        Exchange exchange = new Exchange(stocks, bus, Clock.systemUTC(), null); // no breaker: random flow would trip it
        exchange.start();
        int perProducer = count / producers;
        long[] latency = new long[perProducer * producers];
        List<List<OrderRequest>> flows = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            List<OrderRequest> mixed = new ArrayList<>(perProducer);
            for (int i = 0; i < perProducer; i++) {
                String symbol = SYMBOLS[(i + p) % SYMBOLS.length];
                OrderRequest r = flow(1, p * 1_000_003L + i, symbol).get(0);
                mixed.add(r);
            }
            flows.add(mixed);
        }
        CountDownLatch done = new CountDownLatch(perProducer * producers);
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            int producer = p;
            Thread t = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                List<OrderRequest> mine = flows.get(producer);
                for (int i = 0; i < mine.size(); i++) {
                    long t0 = System.nanoTime();
                    exchange.submit(mine.get(i)).join(); // wait for the answer, like a real client
                    latency[producer * perProducer + i] = System.nanoTime() - t0;
                    done.countDown();
                }
            }, "producer-" + p);
            threads.add(t);
            t.start();
        }
        long start = System.nanoTime();
        go.countDown();
        done.await(5, TimeUnit.MINUTES);
        double seconds = (System.nanoTime() - start) / 1e9;
        exchange.shutdown(10, TimeUnit.SECONDS);
        Arrays.sort(latency);
        System.out.printf("whole exchange: %,d orders in %.2f s%n", latency.length, seconds);
        return new Result(latency.length, seconds, latency);
    }
}
