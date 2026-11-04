package com.dalalx.core.breaker;

import java.time.Instant;
import java.util.Objects;

/**
 * Watches one stock's trades and says when they have moved too far, too fast.
 *
 * <p>A trade trips the breaker if it is {@code limitBp} or more below the
 * highest price of the window (a crash) or that far above the lowest (a
 * spike). Measuring against the window's extremes, not just the first price,
 * catches "up 4%, then down 10%" within the same minute.
 *
 * <p>Not thread-safe: owned by the symbol's worker thread like the book.
 */
public final class CircuitBreaker {

    /** Why the breaker tripped, for the alert and the headline. */
    public record Trip(long fromPrice, long toPrice, long moveBp) {
        public boolean isFall() {
            return toPrice < fromPrice;
        }
    }

    private final BreakerRule rule;
    private final PriceWindow window;

    public CircuitBreaker(BreakerRule rule) {
        this.rule = Objects.requireNonNull(rule, "rule must not be null");
        this.window = new PriceWindow(rule.window());
    }

    /**
     * Records a trade.
     *
     * @return the trip details if this trade moved the price too far, else {@code null}
     */
    public Trip record(Instant time, long price) {
        window.add(time, price);
        long high = window.highest();
        long low = window.lowest();
        if ((high - price) * 10_000 >= rule.limitBp() * high) {
            return new Trip(high, price, (price - high) * 10_000 / high);
        }
        if ((price - low) * 10_000 >= rule.limitBp() * low) {
            return new Trip(low, price, (price - low) * 10_000 / low);
        }
        return null;
    }

    /** Starts a fresh window, e.g. after a halt, so the old move can't trip it again at once. */
    public void reset() {
        window.clear();
    }

    public BreakerRule rule() {
        return rule;
    }
}
