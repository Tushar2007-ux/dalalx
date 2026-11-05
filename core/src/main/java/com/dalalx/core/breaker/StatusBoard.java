package com.dalalx.core.breaker;

import com.dalalx.core.events.AuctionUpdate;
import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.EngineListener;
import com.dalalx.core.events.MarketStatusChanged;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The latest phase of every stock, kept by listening for
 * {@link MarketStatusChanged} (Observer), so web threads can read it without
 * asking the worker threads.
 */
public final class StatusBoard implements EngineListener {

    /** One stock's current phase. {@code reason} and {@code until} may be null. */
    public record Status(MarketPhase phase, String reason, Instant until, Instant since) {
        public static final Status OPEN = new Status(MarketPhase.OPEN, null, null, null);
    }

    private final Map<String, Status> statuses = new ConcurrentHashMap<>();
    private final Map<String, AuctionUpdate> auctions = new ConcurrentHashMap<>();

    @Override
    public void onEvent(EngineEvent event) {
        if (event instanceof MarketStatusChanged e) {
            statuses.put(e.symbol(), new Status(e.phase(), e.reason(), e.until(), e.timestamp()));
            if (e.phase() != MarketPhase.AUCTION) {
                auctions.remove(e.symbol());
            }
        } else if (event instanceof AuctionUpdate e) {
            auctions.put(e.symbol(), e);
        }
    }

    /** @return the latest indicative auction state, while the stock is in a call auction */
    public Optional<AuctionUpdate> auction(String symbol) {
        return Optional.ofNullable(auctions.get(symbol));
    }

    /** @return the stock's phase (OPEN if it has never changed) */
    public Status status(String symbol) {
        return statuses.getOrDefault(symbol, Status.OPEN);
    }
}
