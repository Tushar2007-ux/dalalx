package com.dalalx.core.breaker;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Trading halted by the circuit breaker: new orders are refused (cancels are
 * still allowed, so traders can get out of the book) until {@code endsAt},
 * then the market moves to whatever {@code reopen} decides: straight back to
 * {@link OpenState}, or (Step 13) a call auction.
 *
 * @param endsAt when the halt ends
 * @param why    why trading was halted, shown to traders
 * @param reopen builds the state that follows the halt, given the time it ends
 */
public record HaltedState(Instant endsAt, String why, Function<Instant, MarketState> reopen) implements MarketState {

    public HaltedState {
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        Objects.requireNonNull(why, "why must not be null");
        Objects.requireNonNull(reopen, "reopen must not be null");
    }

    @Override
    public MarketPhase phase() {
        return MarketPhase.HALTED;
    }

    @Override
    public Optional<String> refusal() {
        return Optional.of("trading halted: " + why);
    }

    @Override
    public Optional<String> reason() {
        return Optional.of(why);
    }

    @Override
    public Optional<Instant> until() {
        return Optional.of(endsAt);
    }

    @Override
    public MarketState onClock(Instant now) {
        return now.isBefore(endsAt) ? this : reopen.apply(now);
    }
}
