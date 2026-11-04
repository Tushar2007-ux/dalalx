package com.dalalx.core.events;

import com.dalalx.core.model.Trade;
import java.time.Instant;

/**
 * A trade happened. The {@link Trade} is already an immutable record, so it is
 * carried as-is.
 *
 * @param symbol    stock symbol
 * @param timestamp when it executed
 * @param sequence  global event number
 * @param trade     the trade
 */
public record TradeExecuted(String symbol, Instant timestamp, long sequence, Trade trade)
        implements EngineEvent {
}
