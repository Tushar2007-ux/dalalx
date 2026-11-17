package com.uncross.core.journal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * A clock that normally tells the real time, but can be <em>pinned</em> to a
 * moment from the journal while that moment is being replayed.
 *
 * <p>Everything in the engine that needs the time (trade timestamps, the
 * circuit breaker's 60-second window, the auction's end) asks the
 * {@link Clock} it was given. Handing it this clock means a replayed trade
 * gets the time it originally had, and a replayed crash halts the stock just
 * as it did on the day.
 *
 * <p>Pinning happens on the one thread that does the replaying; the field is
 * {@code volatile} so the symbol workers see each new moment straight away.
 */
public final class ReplayClock extends Clock {

    private final Clock real;
    private volatile Instant pinned;

    public ReplayClock(Clock real) {
        this.real = Objects.requireNonNull(real, "real must not be null");
    }

    /** From now on, until {@link #release()}, the time is {@code moment}. */
    public void pin(Instant moment) {
        this.pinned = Objects.requireNonNull(moment, "moment must not be null");
    }

    /** Back to the real time. */
    public void release() {
        this.pinned = null;
    }

    public boolean isPinned() {
        return pinned != null;
    }

    @Override
    public Instant instant() {
        Instant moment = pinned;
        return moment != null ? moment : real.instant();
    }

    @Override
    public ZoneId getZone() {
        return real.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new ReplayClock(real.withZone(zone));
    }
}
