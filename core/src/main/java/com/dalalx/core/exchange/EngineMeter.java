package com.dalalx.core.exchange;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns two samples of worker counters into rates: commands per second,
 * how busy each thread was, and where its work came from.
 *
 * <p>A pure function with no state and no threads, so it is trivial to test.
 * Counters only ever grow, so "per second" is just
 * {@code (now - before) / elapsed}. A worker that wasn't in the earlier
 * sample is measured from zero.
 */
public final class EngineMeter {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private EngineMeter() {
    }

    /**
     * @param before       the earlier sample (may be empty)
     * @param now          the later sample
     * @param elapsedNanos time between the samples; must be positive
     * @return one load per worker in {@code now}, in the same order
     */
    public static List<WorkerLoad> between(List<WorkerStats> before, List<WorkerStats> now, long elapsedNanos) {
        if (elapsedNanos <= 0) {
            throw new IllegalArgumentException("elapsedNanos must be > 0");
        }
        Map<String, WorkerStats> earlier = before.stream()
                .collect(Collectors.toMap(WorkerStats::symbol, Function.identity(), (a, b) -> b));
        double seconds = elapsedNanos / NANOS_PER_SECOND;
        return now.stream().map(n -> load(earlier.get(n.symbol()), n, seconds, elapsedNanos)).toList();
    }

    private static WorkerLoad load(WorkerStats b, WorkerStats n, double seconds, long elapsedNanos) {
        long processedBefore = b == null ? 0 : b.processed();
        long busyBefore = b == null ? 0 : b.busyNanos();
        double perSecond = Math.max(0, n.processed() - processedBefore) / seconds;
        double utilisation = Math.min(1.0, Math.max(0, n.busyNanos() - busyBefore) / (double) elapsedNanos);
        Map<CommandSource, Double> arrivals = new EnumMap<>(CommandSource.class);
        for (CommandSource source : CommandSource.values()) {
            long was = b == null ? 0 : b.arrivals().getOrDefault(source, 0L);
            long is = n.arrivals().getOrDefault(source, 0L);
            arrivals.put(source, Math.max(0, is - was) / seconds);
        }
        return new WorkerLoad(n.symbol(), n.thread(), n.state(), n.running(), n.pending(), n.capacity(),
                n.processed(), perSecond, utilisation, arrivals);
    }
}
