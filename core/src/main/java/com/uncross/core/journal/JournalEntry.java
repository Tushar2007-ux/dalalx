package com.uncross.core.journal;

import com.uncross.core.exchange.ListedStock;
import com.uncross.core.model.OrderRequest;
import java.time.Instant;
import java.util.Objects;

/**
 * One line of the exchange's journal: a command the exchange carried out.
 *
 * <p>The journal records <em>commands</em> ("this order was entered", "that
 * one was cancelled"), not their results. Feeding the same commands through
 * the same engine again produces the same books and trades, because the
 * matching rules are deterministic. That is the whole idea behind recovery
 * and rewind: <b>the state is a function of the commands</b>.
 *
 * <p>Each entry keeps the order id the exchange gave out at the time. On
 * replay the ids come out differently (the five workers ran in parallel, the
 * replay runs one entry at a time), so the {@link Replayer} translates old ids
 * to new ones before a cancel or amend refers to them.
 *
 * <p><b>Pattern:</b> a sealed hierarchy of immutable records, so a
 * {@code switch} over an entry is checked by the compiler for completeness.
 */
public sealed interface JournalEntry {

    /** When the exchange carried the command out. */
    Instant at();

    /** An order was accepted (rejected orders are not journalled: they changed nothing). */
    record Submitted(Instant at, long orderId, OrderRequest request) implements JournalEntry {
        public Submitted {
            Objects.requireNonNull(at, "at must not be null");
            Objects.requireNonNull(request, "request must not be null");
        }
    }

    /** A trader cancelled a waiting order. */
    record Cancelled(Instant at, long orderId) implements JournalEntry {
        public Cancelled {
            Objects.requireNonNull(at, "at must not be null");
        }
    }

    /**
     * A waiting order was amended. {@code newOrderId} is the replacement's id
     * for a cancel/replace, or the same id for an in-place reduction.
     */
    record Amended(Instant at, long orderId, long price, long qty, long newOrderId) implements JournalEntry {
        public Amended {
            Objects.requireNonNull(at, "at must not be null");
        }
    }

    /** The surveillance desk cancelled everything a suspended trader had waiting. */
    record CancelledAll(Instant at, String traderId) implements JournalEntry {
        public CancelledAll {
            Objects.requireNonNull(at, "at must not be null");
            Objects.requireNonNull(traderId, "traderId must not be null");
        }
    }

    /** A new stock was listed while the market was running. */
    record Listed(Instant at, ListedStock stock) implements JournalEntry {
        public Listed {
            Objects.requireNonNull(at, "at must not be null");
            Objects.requireNonNull(stock, "stock must not be null");
        }
    }

    /** A trader opened an account (the PIN is stored only as a salted hash). */
    record AccountOpened(Instant at, String name, String pinHash) implements JournalEntry {
        public AccountOpened {
            Objects.requireNonNull(at, "at must not be null");
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(pinHash, "pinHash must not be null");
        }
    }
}
