package com.dalalx.core.breaker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

    private static final Instant T0 = Instant.parse("2026-11-04T09:15:00Z");
    private final CircuitBreaker breaker = new CircuitBreaker(BreakerRule.STANDARD);

    private CircuitBreaker.Trip at(int second, long price) {
        return breaker.record(T0.plusSeconds(second), price);
    }

    @Test
    void aTenPercentFallWithinAMinuteTrips() {
        at(0, 100_000);
        at(20, 95_000);
        CircuitBreaker.Trip trip = at(50, 90_000);

        assertThat(trip).isEqualTo(new CircuitBreaker.Trip(100_000, 90_000, -1_000));
        assertThat(trip.isFall()).isTrue();
    }

    @Test
    void justUnderTenPercentDoesNot() {
        at(0, 100_000);
        assertThat(at(30, 90_005)).isNull();
    }

    @Test
    void theSameFallSpreadOverMoreThanAMinuteDoesNot() {
        at(0, 100_000);
        at(40, 95_000);
        assertThat(at(61, 90_000)).isNull(); // the ₹1,000 high has left the window; ₹950 → ₹900 is 5.3%
    }

    @Test
    void measuresFromTheHighestPointNotTheFirst() {
        at(0, 100_000);
        at(10, 104_000);                     // up 4% first
        assertThat(at(30, 93_500)).isNotNull(); // then down 10.1% from the high
    }

    @Test
    void aSpikeUpTripsToo() {
        at(0, 100_000);
        CircuitBreaker.Trip trip = at(5, 110_000);
        assertThat(trip.isFall()).isFalse();
        assertThat(trip.moveBp()).isEqualTo(1_000);
    }

    @Test
    void resetForgetsTheOldPrices() {
        at(0, 100_000);
        breaker.reset();
        assertThat(at(1, 90_000)).isNull();
    }
}
