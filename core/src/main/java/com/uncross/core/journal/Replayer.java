package com.uncross.core.journal;

import com.uncross.core.exchange.Exchange;
import com.uncross.core.journal.JournalEntry.AccountOpened;
import com.uncross.core.journal.JournalEntry.Amended;
import com.uncross.core.journal.JournalEntry.Cancelled;
import com.uncross.core.journal.JournalEntry.CancelledAll;
import com.uncross.core.journal.JournalEntry.Listed;
import com.uncross.core.journal.JournalEntry.Submitted;
import com.uncross.core.matching.AmendResult;
import com.uncross.core.matching.MatchResult;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Feeds journal entries back into an {@link Exchange}, one at a time, through
 * exactly the same doors the original commands came in by.
 *
 * <p>There is no special "restore" code path: a replayed order is submitted,
 * matched and published like any other, so every listener (trade history,
 * candles, ledgers, accounts) rebuilds itself just by hearing the events
 * again. That is what makes replay trustworthy: it cannot drift from the
 * real engine, because it <em>is</em> the real engine.
 *
 * <p>Two details make it exact:
 * <ul>
 *   <li>Before each entry the {@link ReplayClock} is pinned to the entry's
 *       time, so trades get their original timestamps and the circuit
 *       breaker sees the same timing.</li>
 *   <li>Order ids come out differently on replay, so the replayer keeps a
 *       map from each journalled id to the id the order has now, and
 *       translates every cancel and amend through it.</li>
 * </ul>
 *
 * <p>The replayer waits for each command to finish before the next, so one
 * thread drives the whole replay in journal order.
 */
public final class Replayer {

    private final Exchange exchange;
    private final ReplayClock clock;
    private final Consumer<AccountOpened> accounts;
    private final Map<Long, Long> ids = new HashMap<>();
    private int orders;

    public Replayer(Exchange exchange, ReplayClock clock, Consumer<AccountOpened> accounts) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
    }

    /** Carries out one entry again, at its original time. */
    public void apply(JournalEntry entry) {
        clock.pin(entry.at());
        switch (entry) {
            case Submitted s -> {
                if (exchange.stock(s.request().symbol()).isPresent()) {
                    MatchResult result = exchange.submit(s.request()).join();
                    ids.put(s.orderId(), result.incoming().id());
                    orders++;
                }
            }
            case Cancelled c -> exchange.cancel(now(c.orderId())).join();
            case Amended a -> {
                AmendResult result = exchange.amend(now(a.orderId()), a.price(), a.qty()).join();
                if (result.amended()) {
                    ids.put(a.newOrderId(), result.order().id());
                }
            }
            case CancelledAll c -> exchange.cancelAllFor(c.traderId()).join();
            case Listed l -> {
                if (exchange.stock(l.stock().symbol()).isEmpty()) {
                    exchange.list(l.stock());
                }
            }
            case AccountOpened o -> accounts.accept(o);
        }
    }

    /** The id an order has now, given the id the journal knew it by. */
    private long now(long journalledId) {
        return ids.getOrDefault(journalledId, -1L);
    }

    /** How many orders have been re-entered so far. */
    public int orders() {
        return orders;
    }
}
