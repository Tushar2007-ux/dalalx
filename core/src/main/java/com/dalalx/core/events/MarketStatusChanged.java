package com.dalalx.core.events;

import com.dalalx.core.breaker.MarketPhase;
import java.time.Instant;

/**
 * A stock changed trading phase: halted by the circuit breaker, reopened,
 * or (Step 13) into or out of a call auction.
 *
 * @param symbol    the stock
 * @param timestamp when it changed
 * @param sequence  global event order
 * @param phase     the new phase
 * @param reason    why (e.g. "RELIANCE fell 10.2% in 60 seconds ..."), or {@code null}
 * @param until     when this phase ends by itself, or {@code null}
 */
public record MarketStatusChanged(String symbol, Instant timestamp, long sequence, MarketPhase phase,
                                  String reason, Instant until) implements EngineEvent {
}
