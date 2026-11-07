package com.dalalx.core.exchange;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A live stress test for the Engine Room: many producer threads fire
 * thousands of commands at every worker at once, so the audience can watch
 * the queues fill and drain.
 *
 * <p>The commands are harmless: each one asks its worker for a five-level
 * snapshot of the book. They go through the same {@link SymbolWorker} queue as
 * real orders (so they really compete for the worker thread), but they never
 * change a book, publish an event or appear on the tape, so the market itself
 * is untouched.
 *
 * <p>It shows the two ideas from the concurrency viva questions at full speed:
 * <b>producer–consumer</b> (eight producers, one consumer per stock) and
 * <b>thread confinement</b> (however many threads push, each book is still
 * read by its one worker).
 */
public final class RushHour {

    /** What happened: how many commands ran and how long the whole rush took. */
    public record Result(int producers, int commands, long millis) {
        /** @return commands handled per second over the whole rush */
        public long perSecond() {
            return millis == 0 ? commands * 1000L : commands * 1000L / millis;
        }
    }

    private RushHour() {
    }

    /**
     * Starts the rush in the background.
     *
     * @param exchange  where to send the commands
     * @param producers how many producer threads (1–32)
     * @param each      commands per producer (1–20,000), spread over every stock
     * @return completes when every command has been handled by its worker
     */
    public static CompletableFuture<Result> start(Exchange exchange, int producers, int each) {
        if (producers < 1 || producers > 32 || each < 1 || each > 20_000) {
            throw new IllegalArgumentException("producers must be 1–32 and each 1–20,000");
        }
        List<String> symbols = exchange.stocks().stream().map(ListedStock::symbol).toList();
        ExecutorService pool = Executors.newFixedThreadPool(producers, r -> {
            Thread t = new Thread(r, "rush-producer");
            t.setDaemon(true);
            return t;
        });
        long started = System.nanoTime();
        AtomicInteger sent = new AtomicInteger();
        List<CompletableFuture<?>> all = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            int offset = p;
            all.add(CompletableFuture.supplyAsync(() -> fire(exchange, symbols, offset, each, sent), pool)
                    .thenCompose(futures -> CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))));
        }
        return CompletableFuture.allOf(all.toArray(CompletableFuture[]::new))
                .handle((done, error) -> {
                    pool.shutdown();
                    return new Result(producers, sent.get(), (System.nanoTime() - started) / 1_000_000);
                });
    }

    /** One producer's share: round-robin over the stocks so every worker gets busy. */
    private static List<CompletableFuture<?>> fire(Exchange exchange, List<String> symbols, int offset, int each,
                                                   AtomicInteger sent) {
        List<CompletableFuture<?>> futures = new ArrayList<>(each);
        for (int i = 0; i < each; i++) {
            String symbol = symbols.get((offset + i) % symbols.size());
            futures.add(exchange.onWorker(symbol, "rush", CommandSource.PRESENTER, engine -> engine.snapshot(5))
                    .exceptionally(tooBusy -> null)); // a refused command (queue full) is just not counted twice
            sent.incrementAndGet();
        }
        return futures;
    }
}
