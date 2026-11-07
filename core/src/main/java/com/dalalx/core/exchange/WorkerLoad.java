package com.dalalx.core.exchange;

import java.util.Map;

/**
 * How hard one worker thread worked between two {@link WorkerStats} samples.
 *
 * @param symbol            the stock this worker owns
 * @param thread            the worker thread's name
 * @param state             the thread's state at the second sample
 * @param running           the command being run at the second sample, or {@code null}
 * @param pending           commands waiting in the queue at the second sample
 * @param capacity          the queue's maximum size
 * @param processed         commands run since start
 * @param perSecond         commands run per second over the interval
 * @param utilisation       share of the interval spent running commands, 0.0 to 1.0
 * @param arrivalsPerSecond commands received per second over the interval, per source
 */
public record WorkerLoad(String symbol, String thread, String state, String running, int pending, int capacity,
                         long processed, double perSecond, double utilisation,
                         Map<CommandSource, Double> arrivalsPerSecond) {

    public WorkerLoad {
        arrivalsPerSecond = Map.copyOf(arrivalsPerSecond);
    }
}
