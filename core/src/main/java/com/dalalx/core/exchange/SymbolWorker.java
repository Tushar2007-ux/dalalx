package com.dalalx.core.exchange;

import com.dalalx.core.matching.SymbolEngine;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
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
 * <p><b>Shutdown:</b> {@link #shutdown()} stops new commands, then queues a
 * special "poison pill". Commands already queued ahead of it still run (the
 * queue drains), then the worker exits.
 */
final class SymbolWorker {

    private static final Logger LOG = System.getLogger(SymbolWorker.class.getName());
    static final int QUEUE_CAPACITY = 100_000;

    private final SymbolEngine engine;
    private final BlockingQueue<Command<?>> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final Command<Void> poisonPill = Command.of("stop", engine -> null);
    private final Thread thread;
    private volatile boolean accepting = true;

    /**
     * The engine this worker owns. Only for setup before {@link #start()};
     * after that, reach it through {@link #enqueue} like everyone else.
     */
    SymbolEngine engine() {
        return engine;
    }

    SymbolWorker(SymbolEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
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
        Command<T> command = Command.of(name, action);
        if (!accepting) {
            command.result().completeExceptionally(
                    new RejectedExecutionException(engine.symbol() + " worker is shut down"));
            return command.result();
        }
        try {
            queue.put(command); // blocks if 100,000 commands are already waiting (back-pressure)
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
            command.execute(engine);
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

    /** @return commands waiting to run (approximate; for monitoring) */
    int pending() {
        return queue.size();
    }
}
