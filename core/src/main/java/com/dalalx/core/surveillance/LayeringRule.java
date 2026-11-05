package com.dalalx.core.surveillance;

import com.dalalx.core.bots.Ticks;
import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.OrderCancelled;
import com.dalalx.core.matching.SymbolEngine;
import com.dalalx.core.model.Money;
import com.dalalx.core.model.Side;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * <b>Layering:</b> stacking orders at several nearby prices on one side to
 * fake depth, and pulling them almost immediately.
 *
 * <p>Rule: a trader cancelled orders at {@value #MIN_LEVELS} or more different
 * prices within {@value #MAX_TICKS} ticks of each other, on the same side of
 * one stock, each less than a second after placing it, while they were all in
 * the book at the same moment.
 */
public final class LayeringRule implements SurveillanceRule {

    static final int MIN_LEVELS = 3;
    static final long MAX_TICKS = 5;
    static final long FAST_MILLIS = 1_000;
    static final Duration WINDOW = Duration.ofSeconds(10);

    private record Key(String traderId, String symbol, Side side) {
    }

    /** A quickly cancelled order: {@code time} is when it was pulled. */
    private record Pulled(Instant time, Instant placedAt, long orderId, long price, long qty) implements Windows.Timed {
    }

    private final Windows.Sliding<Key, Pulled> pulled = new Windows.Sliding<>(WINDOW);
    private final Windows.Cooldown<Key> cooldown = new Windows.Cooldown<>(Duration.ofSeconds(60));

    @Override
    public String id() {
        return "layering";
    }

    @Override
    public String name() {
        return "Layering";
    }

    @Override
    public String threshold() {
        return "≥ 3 stacked levels within 5 ticks, cancelled < 1 s";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public List<Finding> inspect(EngineEvent event, ActivityView activity) {
        if (!(event instanceof OrderCancelled e) || !SymbolEngine.CANCELLED_BY_TRADER.equals(e.reason())) {
            return List.of();
        }
        OrderLife life = activity.life(e.order().id());
        if (life == null || life.lifeMillis() < 0 || life.lifeMillis() >= FAST_MILLIS || life.price() == 0) {
            return List.of();
        }
        Key key = new Key(life.traderId(), life.symbol(), life.side());
        Deque<Pulled> window = pulled.add(key, new Pulled(e.timestamp(), life.placedAt(), life.orderId(), life.price(), life.qty()));
        return check(key, window, e.timestamp());
    }

    /**
     * Looks for a moment when 3+ of the trader's soon-pulled orders were in the
     * book <em>at the same time</em>, at distinct prices within the band. A
     * market maker that cancels one quote and then places the next one is
     * never "stacked"; a layerer's orders all sit there together.
     */
    private List<Finding> check(Key key, Deque<Pulled> window, Instant now) {
        long bandPaise = MAX_TICKS * Ticks.TICK;
        for (Pulled moment : window) {
            Instant t = moment.placedAt();
            List<Pulled> live = window.stream()
                    .filter(p -> !p.placedAt().isAfter(t) && p.time().isAfter(t)).toList();
            TreeSet<Long> prices = live.stream().map(Pulled::price).collect(Collectors.toCollection(TreeSet::new));
            for (long low : prices) {
                NavigableSet<Long> band = prices.subSet(low, true, low + bandPaise, true);
                if (band.size() >= MIN_LEVELS && cooldown.tryReport(key, now)) {
                    pulled.clear(key);
                    return List.of(finding(key, band, live.stream().filter(p -> band.contains(p.price()))
                            .map(Pulled::orderId).collect(Collectors.toSet())));
                }
            }
        }
        return List.of();
    }

    private static Finding finding(Key key, NavigableSet<Long> band, Set<Long> flagged) {
        Map<String, String> figures = new LinkedHashMap<>();
        figures.put("Levels stacked", String.valueOf(band.size()));
        figures.put("Band", Money.format(band.first()) + "–" + Money.format(band.last()));
        figures.put("Orders pulled", String.valueOf(flagged.size()));
        figures.put("Each lived", "< 1 s");
        return new Finding(key.traderId(), key.symbol(),
                band.size() + " price levels stacked on the " + (key.side() == Side.BUY ? "bid" : "ask")
                        + " · each cancelled in under a second",
                figures, flagged, new String[] {"The same stack on neighbouring ticks.", "Lifted before it could trade."});
    }
}
