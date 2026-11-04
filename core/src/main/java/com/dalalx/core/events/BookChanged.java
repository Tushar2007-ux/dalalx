package com.dalalx.core.events;

import com.dalalx.core.book.BookSnapshot;
import java.time.Instant;

/**
 * The order book changed; here is its new top. Published once after each
 * command that changed the book, not once per individual fill.
 *
 * @param symbol    stock symbol
 * @param timestamp when the change finished
 * @param sequence  global event number
 * @param book      immutable snapshot of the top levels
 */
public record BookChanged(String symbol, Instant timestamp, long sequence, BookSnapshot book)
        implements EngineEvent {
}
