package com.dalalx.core.breaker;

import java.time.Duration;
import java.util.Objects;

/**
 * The circuit-breaker settings: if a stock moves more than {@code limitBp}
 * basis points (1000 bp = 10%) within {@code window}, trading stops for
 * {@code haltFor}.
 *
 * @param limitBp the move that trips the breaker, in basis points
 * @param window  how far back the move is measured
 * @param haltFor how long trading stays halted
 */
public record BreakerRule(long limitBp, Duration window, Duration haltFor) {

    /** 10% in 60 seconds halts trading for 30 seconds. */
    public static final BreakerRule STANDARD = new BreakerRule(1_000, Duration.ofSeconds(60), Duration.ofSeconds(30));

    public BreakerRule {
        if (limitBp <= 0) {
            throw new IllegalArgumentException("limitBp must be > 0");
        }
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(haltFor, "haltFor must not be null");
        if (window.isNegative() || window.isZero() || haltFor.isNegative() || haltFor.isZero()) {
            throw new IllegalArgumentException("window and haltFor must be positive");
        }
    }
}
