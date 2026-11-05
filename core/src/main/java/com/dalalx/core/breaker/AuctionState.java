package com.dalalx.core.breaker;

import com.dalalx.core.model.OrderType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A call auction (the phase between a halt and normal trading): limit orders
 * are collected but <em>not</em> matched; an indicative price is published as
 * they arrive; when the auction ends everything that crosses trades at one
 * single price and continuous trading resumes.
 *
 * <p>Another {@link MarketState}: adding this phase needed no change to
 * {@link OpenState} or {@link HaltedState}, only a different "reopen" function
 * in {@link TradingSession#withBreaker}.
 *
 * @param endsAt when the auction closes and the book is uncrossed
 */
public record AuctionState(Instant endsAt) implements MarketState {

    public static final String LIMIT_ONLY = "only limit orders are accepted during the call auction";

    public AuctionState {
        Objects.requireNonNull(endsAt, "endsAt must not be null");
    }

    @Override
    public MarketPhase phase() {
        return MarketPhase.AUCTION;
    }

    @Override
    public Optional<String> refusal() {
        return Optional.empty();
    }

    /** Market and IOC orders need something to trade against right now, which an auction can't offer. */
    @Override
    public Optional<String> refusal(OrderType type) {
        return type == OrderType.LIMIT ? Optional.empty() : Optional.of(LIMIT_ONLY);
    }

    @Override
    public boolean matchesContinuously() {
        return false;
    }

    @Override
    public Optional<String> reason() {
        return Optional.of("call auction reopening trading after the circuit breaker");
    }

    @Override
    public Optional<Instant> until() {
        return Optional.of(endsAt);
    }

    @Override
    public MarketState onClock(Instant now) {
        return now.isBefore(endsAt) ? this : OpenState.INSTANCE;
    }
}
