package com.dalalx.core.breaker;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.events.EventBus;
import com.dalalx.core.events.MarketStatusChanged;
import com.dalalx.core.events.OrderRejected;
import com.dalalx.core.events.RecordingListener;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.matching.MatchingStrategies;
import com.dalalx.core.matching.OrderFactory;
import com.dalalx.core.matching.SymbolEngine;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import com.dalalx.core.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** The circuit breaker inside a real engine: halt, refuse, reopen. */
class SymbolEngineBreakerTest {

    /** The standard 10%/60 s/30 s rule, reopening straight into trading (the auction has its own test). */
    static final BreakerRule NO_AUCTION = new BreakerRule(1_000, Duration.ofSeconds(60), Duration.ofSeconds(30));

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T09:15:00Z"));
    private final AtomicLong ids = new AtomicLong();
    private final EventBus bus = new EventBus();
    private final RecordingListener events = new RecordingListener();
    private final SymbolEngine engine = new SymbolEngine("TCS",
            new OrderFactory(ids::incrementAndGet, ids::incrementAndGet, clock),
            MatchingStrategies.standard(ids::incrementAndGet, clock), bus, clock,
            TradingSession.withBreaker("TCS", NO_AUCTION));

    SymbolEngineBreakerTest() {
        bus.register(events);
    }

    private MatchResult order(String trader, Side side, OrderType type, long price, long qty) {
        return engine.submit(new OrderRequest("TCS", trader, side, type, price, qty));
    }

    /** Bids at ₹1,000, ₹950 and ₹890; then a market sell walks through all three. */
    private void crash() {
        order("MUNIM", Side.BUY, OrderType.LIMIT, 100_000, 10);
        order("MUNIM", Side.BUY, OrderType.LIMIT, 95_000, 10);
        order("MUNIM", Side.BUY, OrderType.LIMIT, 89_000, 10);
        order("PANIC", Side.SELL, OrderType.MARKET, 0, 30);
    }

    @Test
    void aCrashHaltsTradingAfterTheTradesAreAnnounced() {
        crash();

        assertThat(events.eventsOf(TradeExecuted.class)).hasSize(3);
        MarketStatusChanged halt = events.eventsOf(MarketStatusChanged.class).get(0);
        assertThat(halt.phase()).isEqualTo(MarketPhase.HALTED);
        assertThat(halt.reason()).isEqualTo("TCS fell 11.0% in 60 seconds (₹1,000.00 → ₹890.00)");
        assertThat(halt.until()).isEqualTo(clock.instant().plusSeconds(30));
        assertThat(events.events().get(events.events().size() - 1)).isSameAs(halt); // last word
    }

    @Test
    void ordersAreRefusedDuringTheHaltButCancelsStillWork() {
        long resting = order("YOU", Side.SELL, OrderType.LIMIT, 120_000, 5).incoming().id();
        crash();

        MatchResult refused = order("YOU", Side.BUY, OrderType.LIMIT, 89_000, 1);

        assertThat(refused.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(refused.reason()).startsWith("trading halted: TCS fell");
        assertThat(events.eventsOf(OrderRejected.class)).hasSize(1);
        assertThat(engine.cancel(resting).cancelled()).isTrue();
    }

    @Test
    void tradingResumesWhenTheHaltEnds() {
        crash();
        clock.advance(Duration.ofSeconds(30));
        engine.tick();

        assertThat(events.eventsOf(MarketStatusChanged.class)).extracting(MarketStatusChanged::phase)
                .containsExactly(MarketPhase.HALTED, MarketPhase.OPEN);
        order("MUNIM", Side.BUY, OrderType.LIMIT, 89_000, 10);
        assertThat(order("PANIC", Side.SELL, OrderType.LIMIT, 89_000, 10).trades()).hasSize(1);
    }
}
