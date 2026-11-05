package com.dalalx.core.events;

import java.time.Instant;

/**
 * The state of a running call auction, published after every change to its
 * book: where it would trade if it closed now, and how much interest there is.
 *
 * @param symbol    the stock
 * @param timestamp when
 * @param sequence  global event order
 * @param price     the indicative price, or {@code null} if nothing would trade yet
 * @param volume    shares that would trade at that price
 * @param buyQty    shares bid at or above it (all bids if no price)
 * @param sellQty   shares offered at or below it (all asks if no price)
 * @param endsAt    when the auction closes
 */
public record AuctionUpdate(String symbol, Instant timestamp, long sequence, Long price, long volume,
                            long buyQty, long sellQty, Instant endsAt) implements EngineEvent {
}
