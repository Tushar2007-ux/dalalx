package com.dalalx.core.breaker;

import java.time.Instant;
import java.util.Optional;

/** Continuous trading: everything is allowed and nothing ends by itself. */
public final class OpenState implements MarketState {

    public static final OpenState INSTANCE = new OpenState();

    private OpenState() {
    }

    @Override
    public MarketPhase phase() {
        return MarketPhase.OPEN;
    }

    @Override
    public Optional<String> refusal() {
        return Optional.empty();
    }

    @Override
    public Optional<Instant> until() {
        return Optional.empty();
    }

    @Override
    public MarketState onClock(Instant now) {
        return this;
    }
}
