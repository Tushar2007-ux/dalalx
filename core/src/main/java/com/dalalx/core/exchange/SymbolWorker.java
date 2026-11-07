package com.dalalx.core.exchange;

import com.dalalx.core.matching.SymbolEngine;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * The one thread that owns one symbol's {@link SymbolEngine}.
 *
 * <p><b>Thread confinement:</b> the order book has no locks and is not
 * thread-safe. That's safe because exactly one thread, this worker's, ever
 * touches it. Every other thread (REST requests, bots, the browser) puts a
 * {@link Command} on this worker's {@link BlockingQueue}. The worker takes
 * commands one at a time, in arrival order, and runs them. So commands for
 * one symbol never overlap, and arrival order is preserved: the fairness
 * rule a matching engine needs.
 *
 * <p><b>Producer–consumer:</b> this is the bounded-buffer problem from
 * Operating Systems. Producers ({@link #enqueue}) put; one consumer (the
 * worker loop) takes. {@code BlockingQueue.take()} sleeps while the queue is
 * empty (no busy-waiting), and {@code put()} sleeps while it's full
 * (back-pressure). The queue handles the locking and condition variables
 * internally, so this class doesn't need any.
 *
 * <p>Different symbols have different workers, so RELIANCE and TCS really
 * match in parallel on different CPU cores.
 *
 * <p><b>Monitoring:</b> the worker also counts what it does (commands run,
 * time spent running them, and arrivals per {@link CommandSource}) so the
 * Engine Room can show each thread working. The counters are
 * {@link LongAdder}s: producers on many threads add arrivals without
 * contending, and readers on other threads see an up-to-date sum.
 *
 * <p><b>Shutdown:</b> {@link #shutdown()} stops new commands, then queues a
 * special "poison pill". Commands already queued ahead of it still run (the
 * queue drains), then the worker exits.
 */
final class SymbolWorker {

    private static final Logger LOG = System.getLogger(SymbolWorker.class.getName());
    static final int QUEUE_CAPACITY = 100_000;

    /** How long a producer waits for room in a full queue before the command is refused. */
    static final long OFFER_TIMEOUT_MS = 1_000;

    private final SymbolEngine engine;
    private final BlockingQueue<Command<?>> queue;
    private final Command<Void> poisonPill = Command.of("stop", engine -> null);
    private final Thread thread;
    private volatile boolean accepting = true;
    private final int capacity;
    private final LongAdder processed = new LongAdder();
    private final LongAdder busyNanos = new LongAdder();
    private final Map<CommandSource, LongAdder> arrivals = new EnumMap<>(CommandSource.class);

    /** The command running right now (written only by the worker; volatile so monitors can read it). */
    private volatile String running;

    /**
     * The engine this worker owns. Only for setup before {@link #start()};
     * after that, reach it through {@link #enqueue} like everyone else.
     */
    SymbolEngine engine() {
        return engine;
    }

    SymbolWorker(SymbolEngine engine) {
        this(engine, QUEUE_CAPACITY);
    }

    /** A worker with a custom queue size (tests use a tiny one to fill it). */
    SymbolWorker(SymbolEngine engine, int capacity) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.capacity = capacity;
        // Filled once here and never changed afterwards, so the EnumMap is safe to read from any thread.
        for (CommandSource source : CommandSource.values()) {
            arrivals.put(source, new LongAdder());
        }
        this.thread = new Thread(this::runLoop, "worker-" + engine.symbol());
    }

    void start() {
        thread.start();
    }

    /**
     * Queues {@code action} to run on this worker's thread.
     *
     * @return a future completed with the action's result
     */
    <T> CompletableFuture<T> enqueue(String name, Function<SymbolEngine, T> action) {
        return enqueue(name, CommandSource.SYSTEM, action);
    }

    /**
     * Queues {@code action}, remembering where it came from (for monitoring only).
     *
     * @return a future completed with the action's result
     */
    <T> CompletableFuture<T> enqueue(String name, CommandSource source, Function<SymbolEngine, T> action) {
        Command<T> command = Command.of(name, action);
        arrivals.get(source).increment();
        if (!accepting) {
            command.result().completeExceptionally(
                    new RejectedExecutionException(engine.symbol() + " worker is shut down"));
            return command.result();
        }
        try {
            // Back-pressure: if 100,000 commands are already waiting, wait up to a second for room,
            // then refuse rather than block the caller (a web thread, the bots) for ever.
            if (!queue.offer(command, OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                command.result().completeExceptionally(
                        new RejectedExecutionException(engine.symbol() + " is too busy; try again"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            command.result().completeExceptionally(e);
        }
        return command.result();
    }

    /** The consumer loop. Runs only on this worker's own thread. */
    private void runLoop() {
        while (true) {
            Command<?> command;
            try {
                command = queue.take(); // sleeps until there is work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (command == poisonPill) {
                return;
            }
            run(command);
        }
    }

    /** Runs one command and updates the counters. Only called on this worker's thread. */
    private void run(Command<?> command) {
        running = command.name();
        long started = System.nanoTime();
        try {
            command.execute(engine);
        } finally {
            busyNanos.add(System.nanoTime() - started);
            processed.increment();
            running = null;
        }
    }

    /** Stops accepting commands, lets queued ones finish, then ends the thread. */
    void shutdown() {
        accepting = false;
        try {
            queue.put(poisonPill);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** @return {@code true} if the thread ended within the timeout */
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        thread.join(unit.toMillis(timeout));
        if (thread.isAlive()) {
            LOG.log(Level.WARNING, thread.getName() + " did not stop in time");
            return false;
        }
        return true;
    }

    String symbol() {
        return engine.symbol();
    }

    /** @return a moment-in-time picture of this worker's counters (safe to call from any thread) */
    WorkerStats stats() {
        Map<CommandSource, Long> counts = new EnumMap<>(CommandSource.class);
        arrivals.forEach((source, adder) -> counts.put(source, adder.sum()));
        return new WorkerStats(engine.symbol(), thread.getName(), thread.getState().name(), running,
                queue.size(), capacity, processed.sum(), busyNanos.sum(), counts);
    }

    /** @return commands waiting to run (approximate; for monitoring) */
    int pending() {
        return queue.size();
    }
}
