package com.dalalx.server.stream;

import com.dalalx.core.exchange.EngineMeter;
import com.dalalx.core.exchange.Exchange;
import com.dalalx.core.exchange.WorkerLoad;
import com.dalalx.core.exchange.WorkerStats;
import java.time.Instant;
import java.util.List;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Samples every worker thread's counters a few times a second and pushes the
 * rates to {@code /topic/engine}, for the Engine Room page.
 *
 * <p>Reading the counters doesn't go through the workers' queues: they are
 * {@code LongAdder}s and {@code volatile} fields, safe to read from this
 * scheduler thread while the workers keep matching. So watching the engine
 * never slows it down.
 */
@Component
public class EngineRoomBridge {

    /** One push: when it was taken and the load on each worker. */
    public record EngineSnapshot(Instant at, List<WorkerLoad> workers) {
    }

    private final Exchange exchange;
    private final SimpMessagingTemplate broker;
    private List<WorkerStats> previous = List.of();
    private long previousAt = System.nanoTime();
    private volatile EngineSnapshot latest = new EngineSnapshot(Instant.now(), List.of());

    public EngineRoomBridge(Exchange exchange, SimpMessagingTemplate broker) {
        this.exchange = exchange;
        this.broker = broker;
    }

    /** Every 250 ms: measure, remember and broadcast. Runs on Spring's single scheduler thread. */
    @Scheduled(fixedRateString = "${dalalx.engine.sample-ms:250}")
    public void sample() {
        List<WorkerStats> now = exchange.workerStats();
        long at = System.nanoTime();
        latest = new EngineSnapshot(Instant.now(), EngineMeter.between(previous, now, Math.max(1, at - previousAt)));
        previous = now;
        previousAt = at;
        broker.convertAndSend("/topic/engine", latest);
    }

    /** @return the most recent measurement (for a page that has just opened) */
    public EngineSnapshot latest() {
        return latest;
    }
}
