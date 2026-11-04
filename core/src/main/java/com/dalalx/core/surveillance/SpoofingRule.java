package com.dalalx.core.surveillance;

import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.OrderAccepted;
import com.dalalx.core.events.OrderCancelled;
import com.dalalx.core.matching.SymbolEngine;
import com.dalalx.core.model.Money;
import com.dalalx.core.surveillance.Windows.TraderSymbol;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Spoofing:</b> placing large orders you never mean to fill, to make the
 * market look stronger (or weaker) than it is, then cancelling them.
 *
 * <p>Rule: over the last 30 seconds, a trader's large orders (5,000 shares or
 * more) in one stock were more than 85% cancelled, across at least
 * {@value #MIN_ORDERS} such orders.
 */
public final class SpoofingRule implements SurveillanceRule {

    static final long LARGE = 5_000;
    static final int MIN_ORDERS = 3;
    static final long MAX_CANCEL_PERCENT = 85;
    static final Duration WINDOW = Duration.ofSeconds(30);

    /** One large order and how much of it was cancelled so far. */
    private static final class Large implements Windows.Timed {
        final long orderId;
        final Instant time;
        final long qty;
        long cancelled;
        Instant cancelledAt;

        Large(long orderId, Instant time, long qty) {
            this.orderId = orderId;
            this.time = time;
            this.qty = qty;
        }

        @Override
        public Instant time() {
            return time;
        }
    }

    private final Windows.Sliding<TraderSymbol, Large> large = new Windows.Sliding<>(WINDOW);
    private final Map<Long, Large> byId = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Large> eldest) {
            return size() > 10_000;
        }
    };
    private final Windows.Cooldown<TraderSymbol> cooldown = new Windows.Cooldown<>(Duration.ofSeconds(60));

    @Override
    public String id() {
        return "spoofing";
    }

    @Override
    public String name() {
        return "Spoofing";
    }

    @Override
    public String threshold() {
        return "Cancel ratio > 85% in 30 s on orders ≥ 5,000";
    }

    @Override
    public Severity severity() {
        return Severity.CRITICAL;
    }

    @Override
    public List<Finding> inspect(EngineEvent event, ActivityView activity) {
        if (event instanceof OrderAccepted e && e.order().originalQty() >= LARGE) {
            Large entry = new Large(e.order().id(), e.timestamp(), e.order().originalQty());
            byId.put(entry.orderId, entry);
            large.add(new TraderSymbol(e.order().traderId(), e.symbol()), entry);
        } else if (event instanceof OrderCancelled e && SymbolEngine.CANCELLED_BY_TRADER.equals(e.reason())) {
            Large entry = byId.get(e.order().id());
            if (entry != null) {
                entry.cancelled = e.order().remainingQty();
                entry.cancelledAt = e.timestamp();
                return check(new TraderSymbol(e.order().traderId(), e.symbol()), e.timestamp());
            }
        }
        return List.of();
    }

    private List<Finding> check(TraderSymbol key, Instant now) {
        Deque<Large> window = large.get(key, now);
        long placed = window.stream().mapToLong(l -> l.qty).sum();
        long cancelled = window.stream().mapToLong(l -> l.cancelled).sum();
        if (window.size() < MIN_ORDERS || cancelled * 100 <= placed * MAX_CANCEL_PERCENT || !cooldown.tryReport(key, now)) {
            return List.of();
        }
        Set<Long> flagged = new HashSet<>();
        long lifeMillis = 0;
        int counted = 0;
        for (Large l : window) {
            if (l.cancelled > 0) {
                flagged.add(l.orderId);
                lifeMillis += l.cancelledAt.toEpochMilli() - l.time.toEpochMilli();
                counted++;
            }
        }
        long ratio = cancelled * 100 / placed;
        long avgMs = counted == 0 ? 0 : lifeMillis / counted;
        Map<String, String> figures = new LinkedHashMap<>();
        figures.put("Cancel ratio", ratio + "%");
        figures.put("Shares placed", Money.groupIndian(placed));
        figures.put("Avg time to cancel", seconds(avgMs));
        figures.put("Large orders", String.valueOf(window.size()));
        large.clear(key);
        return List.of(new Finding(key.traderId(), key.symbol(),
                window.size() + " large orders · " + ratio + "% cancelled · avg " + seconds(avgMs),
                figures, flagged, new String[] {"Large orders, none meant to fill.", "Pulled within moments of placing."}));
    }

    static String seconds(long millis) {
        return millis / 1000 + "." + String.format("%02d", millis % 1000 / 10) + " s";
    }
}
