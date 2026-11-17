package com.uncross.core.journal;

import com.uncross.core.exchange.Exchange;
import com.uncross.core.journal.JournalEntry.AccountOpened;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Rebuilds the exchange after a restart by replaying the day's journal into
 * it, as fast as it will go.
 *
 * <p>Call it once, after every listener is registered (so they all rebuild)
 * and before anyone can trade (so nothing new slips in between old entries).
 * Afterwards the clock goes back to real time and the exchange carries on
 * from where it stopped.
 */
public final class Recovery {

    /** What a recovery did, for the log and the Engine Room. */
    public record Report(int entries, int orders, Duration took) {
    }

    private Recovery() {
    }

    public static Report replay(List<JournalEntry> entries, Exchange exchange, ReplayClock clock,
                                Consumer<AccountOpened> accounts) {
        long started = System.nanoTime();
        Replayer replayer = new Replayer(exchange, clock, accounts);
        try {
            entries.forEach(replayer::apply);
        } finally {
            clock.release();
        }
        return new Report(entries.size(), replayer.orders(), Duration.ofNanos(System.nanoTime() - started));
    }
}
