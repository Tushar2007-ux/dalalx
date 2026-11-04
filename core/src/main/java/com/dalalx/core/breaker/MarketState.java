package com.dalalx.core.breaker;

import java.time.Instant;
import java.util.Optional;

/**
 * One phase of a stock's trading day: the <b>State pattern</b>.
 *
 * <p>Instead of {@code if (halted) ... else if (auction) ...} scattered through
 * the engine, each phase is its own class that answers the same questions
 * differently. The engine asks its current state and the state decides; a
 * transition simply swaps which object is current. Adding a phase (Step 13's
 * call auction) means adding a class, not editing every {@code if}.
 *
 * <p>States are immutable; a transition returns a new state.
 */
public interface MarketState {

    /** @return which phase this is */
    MarketPhase phase();

    /** @return why a new order must be refused now, or empty if it may trade */
    Optional<String> refusal();

    /** @return why the market is in this phase (e.g. the breaker's trip), if there is a reason */
    default Optional<String> reason() {
        return Optional.empty();
    }

    /** @return when this phase ends by itself, or empty if it lasts until something happens */
    Optional<Instant> until();

    /** @return the state to be in at {@code now} (itself if nothing changes) */
    MarketState onClock(Instant now);
}
