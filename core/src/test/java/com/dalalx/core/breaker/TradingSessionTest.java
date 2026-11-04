package com.dalalx.core.breaker;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TradingSessionTest {

    private static final Instant T0 = Instant.parse("2026-11-04T09:15:00Z");
    private final TradingSession session = TradingSession.withBreaker("TCS", BreakerRule.STANDARD);

    private static Trade trade(int second, long price) {
        return new Trade(1, "TCS", price, 10, 1, 2, "A", "B", Side.SELL, T0.plusSeconds(second));
    }

    @Test
    void startsOpenAndHaltsWhenTheBreakerTrips() {
        assertThat(session.phase()).isEqualTo(MarketPhase.OPEN);
        assertThat(session.onTrade(trade(0, 100_000))).isEmpty();

        MarketState halted = session.onTrade(trade(10, 89_000)).orElseThrow();

        assertThat(halted.phase()).isEqualTo(MarketPhase.HALTED);
        assertThat(halted.until()).contains(T0.plusSeconds(40));
        assertThat(session.refusal()).hasValue("trading halted: TCS fell 11.0% in 60 seconds (₹1,000.00 → ₹890.00)");
    }

    @Test
    void reopensWhenTheHaltRunsOutAndStartsAFreshWindow() {
        session.onTrade(trade(0, 100_000));
        session.onTrade(trade(10, 89_000));

        assertThat(session.onClock(T0.plusSeconds(39))).isEmpty();
        assertThat(session.onClock(T0.plusSeconds(40))).get().extracting(MarketState::phase).isEqualTo(MarketPhase.OPEN);
        assertThat(session.refusal()).isEmpty();
        // The old ₹1,000 high was forgotten, so ₹890 → ₹880 is a small move, not another halt.
        assertThat(session.onTrade(trade(41, 88_000))).isEmpty();
    }

    @Test
    void tradesDuringAHaltAreIgnored() {
        session.onTrade(trade(0, 100_000));
        session.onTrade(trade(10, 89_000));
        assertThat(session.onTrade(trade(11, 50_000))).isEmpty();
    }

    @Test
    void aSessionWithoutBreakerNeverHalts() {
        TradingSession open = TradingSession.alwaysOpen("TCS");
        open.onTrade(trade(0, 100_000));
        assertThat(open.onTrade(trade(1, 10_000))).isEmpty();
    }
}
