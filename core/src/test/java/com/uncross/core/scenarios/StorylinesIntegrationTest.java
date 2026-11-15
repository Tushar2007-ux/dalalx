package com.uncross.core.scenarios;

import static org.assertj.core.api.Assertions.assertThat;

import com.uncross.core.bots.BotRunner;
import com.uncross.core.breaker.MarketPhase;
import com.uncross.core.bots.ExchangeGateway;
import com.uncross.core.bots.LiveMarketView;
import com.uncross.core.bots.StandardBots;
import com.uncross.core.bots.Ticks;
import com.uncross.core.events.EventBus;
import com.uncross.core.events.MarketStatusChanged;
import com.uncross.core.events.RecordingListener;
import com.uncross.core.exchange.Exchange;
import com.uncross.core.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Storylines bending a real market of real bots. */
class StorylinesIntegrationTest {

    private static final Duration TICK = Duration.ofMillis(250);

    private final EventBus bus = new EventBus();
    private final Exchange exchange = new Exchange(bus);
    private final LiveMarketView market = new LiveMarketView(exchange.stocks());
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T09:15:00Z"));
    private BotRunner runner;
    private ScenarioDirector director;

    @AfterEach
    void stop() throws InterruptedException {
        exchange.shutdown(5, TimeUnit.SECONDS);
    }

    private void warmUp() {
        bus.register(market);
        exchange.start();
        runner = new BotRunner(StandardBots.create(), market, new ExchangeGateway(exchange), new Random(7), 250);
        director = new ScenarioDirector(Storylines.all(), runner, clock);
        runner.addHook(director);
        run(Duration.ofSeconds(10));
    }

    private void run(Duration time) {
        for (long t = 0; t < time.toMillis(); t += TICK.toMillis()) {
            clock.advance(TICK);
            runner.tick();
        }
    }

    private long moveBp(String symbol) {
        return Ticks.changeBp(market.referencePrice(symbol), market.lastPrice(symbol));
    }

    @Test
    void flashCrashTripsTheCircuitBreakerOnReliance() {
        RecordingListener events = new RecordingListener();
        bus.register(events);
        warmUp();
        director.start("flash-crash");
        run(Duration.ofSeconds(60));

        assertThat(events.eventsOf(MarketStatusChanged.class))
                .anySatisfy(e -> {
                    assertThat(e.symbol()).isEqualTo("RELIANCE");
                    assertThat(e.phase()).isEqualTo(MarketPhase.HALTED);
                    assertThat(e.reason()).startsWith("RELIANCE fell 1");
                });
        assertThat(moveBp("RELIANCE")).isLessThan(-900);
    }

    @Test
    void bubbleRunsInfyUpThenItEnds() {
        warmUp();
        director.start("bubble");
        run(Duration.ofSeconds(60));
        assertThat(moveBp("INFY")).isGreaterThan(300);

        run(Duration.ofSeconds(70));
        assertThat(director.status().running()).isFalse();
    }
}
