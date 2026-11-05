package com.dalalx.core.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.matching.MatchingStrategies;
import com.dalalx.core.matching.OrderFactory;
import com.dalalx.core.matching.SymbolEngine;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** A full queue refuses new work after a short wait instead of blocking the caller for ever. */
class BackPressureTest {

    @Test
    void aFullQueueRefusesInsteadOfBlocking() {
        AtomicLong ids = new AtomicLong();
        SymbolEngine engine = new SymbolEngine("TCS", new OrderFactory(ids::incrementAndGet, ids::incrementAndGet,
                Clock.systemUTC()), MatchingStrategies.standard(ids::incrementAndGet, Clock.systemUTC()));
        SymbolWorker worker = new SymbolWorker(engine, 1); // never started: nothing drains the queue

        CompletableFuture<Integer> first = worker.enqueue("first", e -> 1);
        long start = System.nanoTime();
        CompletableFuture<Integer> second = worker.enqueue("second", e -> 2);
        long waitedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(first).isNotDone();                      // queued, waiting for the worker
        assertThat(second).isCompletedExceptionally();
        assertThat(waitedMs).isBetween(SymbolWorker.OFFER_TIMEOUT_MS - 50, SymbolWorker.OFFER_TIMEOUT_MS + 2_000);
        try {
            second.get();
        } catch (ExecutionException | InterruptedException e) {
            assertThat(e.getCause()).isInstanceOf(RejectedExecutionException.class).hasMessageContaining("too busy");
        }
    }
}
