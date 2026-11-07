package com.dalalx.core.exchange;

import java.util.Map;

/**
 * A snapshot of one worker thread's counters, for monitoring (the Engine Room).
 *
 * <p>An immutable value, so it can be handed to any thread. The numbers are
 * read without stopping the worker, so they are a moment-in-time picture,
 * not an exact one; that's fine for a dashboard.
 *
 * @param symbol     the stock this worker owns
 * @param thread     the worker thread's name, e.g. {@code worker-RELIANCE}
 * @param state      the thread's state ({@code RUNNABLE}, {@code WAITING}, ...)
 * @param running    the command being run right now, or {@code null} if idle
 * @param pending    commands waiting in the queue
 * @param capacity   the queue's maximum size
 * @param processed  commands run since start
 * @param busyNanos  total time spent running commands
 * @param arrivals   commands received since start, per source
 */
public record WorkerStats(String symbol, String thread, String state, String running, int pending, int capacity,
                          long processed, long busyNanos, Map<CommandSource, Long> arrivals) {

    public WorkerStats {
        arrivals = Map.copyOf(arrivals);
    }
}
