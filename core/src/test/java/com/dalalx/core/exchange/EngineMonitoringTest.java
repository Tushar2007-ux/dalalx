package com.dalalx.core.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.dalalx.core.events.EventBus;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The counters behind the Engine Room: what each worker thread did, and where its work came from. */
class EngineMonitoringTest {

    private final Exchange exchange = new Exchange(Exchange.DEFAULT_STOCKS, new EventBus(), Clock.systemUTC());

    @AfterEach
    void stop() throws InterruptedException {
        exchange.shutdown(5, TimeUnit.SECONDS);
    }

    private void order(String symbol, String trader) {
        exchange.submit(new OrderRequest(symbol, trader, Side.BUY, OrderType.LIMIT, 100000, 1)).join();
    }

    @Test
    void oneStatsEntryPerWorkerThreadInListingOrder() {
        exchange.start();

        assertThat(exchange.workerStats()).extracting(WorkerStats::symbol, WorkerStats::thread)
                .startsWith(org.assertj.core.groups.Tuple.tuple("RELIANCE", "worker-RELIANCE"))
                .hasSize(5);
        assertThat(exchange.workerStats().get(0).capacity()).isEqualTo(SymbolWorker.QUEUE_CAPACITY);
    }

    @Test
    void countsCommandsBySourceUsingTheClassifier() throws InterruptedException {
        exchange.classifySourcesWith(t -> t.equals("YOU") ? CommandSource.PRESENTER
                : t.startsWith("GUEST-") ? CommandSource.AUDIENCE : CommandSource.BOT);
        exchange.start();

        order("TCS", "YOU");
        order("TCS", "GUEST-1");
        order("TCS", "GUEST-2");
        order("TCS", "MUNIM");
        long id = exchange.submit(new OrderRequest("TCS", "GUEST-1", Side.SELL, OrderType.LIMIT, 999999, 1))
                .join().incoming().id();
        exchange.cancel(id).join(); // a cancel is credited to whoever placed the order

        WorkerStats tcs = statsFor("TCS");
        // "processed" is counted just after the caller's future completes, so give the worker a moment.
        for (int i = 0; i < 100 && tcs.processed() < 6; i++) {
            Thread.sleep(10);
            tcs = statsFor("TCS");
        }
        assertThat(tcs.arrivals()).containsEntry(CommandSource.PRESENTER, 1L)
                .containsEntry(CommandSource.AUDIENCE, 4L)
                .containsEntry(CommandSource.BOT, 1L);
        assertThat(tcs.processed()).isGreaterThanOrEqualTo(6); // plus clock ticks
        assertThat(tcs.busyNanos()).isPositive();
        assertThat(statsFor("INFY").arrivals()).containsEntry(CommandSource.AUDIENCE, 0L);
    }

    @Test
    void theClockTicksAreCountedAsClockWork() throws InterruptedException {
        exchange.start();
        Thread.sleep(Exchange.TICK_MILLIS * 3);

        assertThat(statsFor("RELIANCE").arrivals().get(CommandSource.CLOCK)).isGreaterThanOrEqualTo(1L);
    }

    private WorkerStats statsFor(String symbol) {
        return exchange.workerStats().stream().filter(s -> s.symbol().equals(symbol)).findFirst().orElseThrow();
    }

    @Test
    void meterTurnsTwoSamplesIntoRates() {
        WorkerStats before = new WorkerStats("TCS", "worker-TCS", "WAITING", null, 0, 10, 100, 0,
                Map.of(CommandSource.BOT, 50L));
        WorkerStats after = new WorkerStats("TCS", "worker-TCS", "RUNNABLE", "submit", 3, 10, 300, 250_000_000,
                Map.of(CommandSource.BOT, 150L, CommandSource.AUDIENCE, 20L));

        WorkerLoad load = EngineMeter.between(List.of(before), List.of(after), 500_000_000).get(0);

        assertThat(load.perSecond()).isEqualTo(400.0);        // 200 commands in half a second
        assertThat(load.utilisation()).isEqualTo(0.5);        // busy 0.25 s of 0.5 s
        assertThat(load.arrivalsPerSecond()).containsEntry(CommandSource.BOT, 200.0)
                .containsEntry(CommandSource.AUDIENCE, 40.0)
                .containsEntry(CommandSource.CLOCK, 0.0);
        assertThat(load.pending()).isEqualTo(3);
        assertThat(load.running()).isEqualTo("submit");
    }

    @Test
    void meterMeasuresANewWorkerFromZeroAndCapsUtilisation() {
        WorkerStats now = new WorkerStats("INFY", "worker-INFY", "RUNNABLE", null, 0, 10, 10, 2_000_000_000, Map.of());

        WorkerLoad load = EngineMeter.between(List.of(), List.of(now), 1_000_000_000).get(0);

        assertThat(load.perSecond()).isEqualTo(10.0);
        assertThat(load.utilisation()).isEqualTo(1.0);
    }

    @Test
    void meterRejectsANonPositiveInterval() {
        assertThatIllegalArgumentException().isThrownBy(() -> EngineMeter.between(List.of(), List.of(), 0));
    }

    @Test
    void rushHourSendsEveryCommandThroughTheWorkersWithoutTouchingTheMarket() {
        exchange.start();
        long before = statsFor("TCS").arrivals().get(CommandSource.PRESENTER);

        RushHour.Result result = RushHour.start(exchange, 4, 1_000).join();

        assertThat(result.commands()).isEqualTo(4_000);
        assertThat(result.producers()).isEqualTo(4);
        assertThat(result.perSecond()).isPositive();
        assertThat(statsFor("TCS").arrivals().get(CommandSource.PRESENTER) - before).isEqualTo(800); // 4,000 / 5 stocks
        assertThat(exchange.snapshot("TCS", 5).join().bids()).isEmpty();
    }

    @Test
    void rushHourRejectsSillySizes() {
        assertThatIllegalArgumentException().isThrownBy(() -> RushHour.start(exchange, 0, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> RushHour.start(exchange, 4, 50_000));
    }
}
