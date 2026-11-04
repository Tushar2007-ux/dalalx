package com.dalalx.core.events;

import java.time.Instant;

/**
 * Something that happened in the matching engine.
 *
 * <p>A <b>sealed</b> interface: only the event types listed in {@code permits}
 * can implement it. A listener can then {@code switch} over every possible
 * event, and the compiler checks that no case is missing. If a new event type
 * is added later, every exhaustive switch fails to compile until it handles it,
 * so no listener silently ignores the new event.
 *
 * <p>Every event is an immutable {@code record}, so many listener threads can
 * read the same event safely.
 */
public sealed interface EngineEvent
        permits OrderAccepted, OrderRejected, OrderCancelled, TradeExecuted, BookChanged {

    /** @return the stock this event is about */
    String symbol();

    /** @return when it happened */
    Instant timestamp();

    /**
     * @return a global, ever-increasing number. Events with a lower number
     *         happened earlier, across all symbols.
     */
    long sequence();
}
