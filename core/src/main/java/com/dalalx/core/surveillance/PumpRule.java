package com.dalalx.core.surveillance;

import com.dalalx.core.bots.Ticks;
import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.model.Money;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <b>Pump:</b> pushing a price up by buying aggressively, usually to sell into
 * the rise later.
 *
 * <p>Rule: within 30 seconds a stock rose at least 0.5%, and more than 60% of
 * the aggressive buying (buy orders that took liquidity) came from one trader,
 * over at least {@value #MIN_TRADES} trades.
 */
public final class PumpRule implements SurveillanceRule {

    static final long MIN_RISE_BP = 50;
    static final long MIN_SHARE_PERCENT = 60;
    static final int MIN_TRADES = 8;
    static final Duration WINDOW = Duration.ofSeconds(30);

    private record Print(Instant time, long price, long qty, String aggressiveBuyer, long buyOrderId)
            implements Windows.Timed {
    }

    private final Windows.Sliding<String, Print> prints = new Windows.Sliding<>(WINDOW);
    private final Windows.Cooldown<String> cooldown = new Windows.Cooldown<>(Duration.ofSeconds(120));

    @Override
    public String id() {
        return "pump";
    }

    @Override
    public String name() {
        return "Pump";
    }

    @Override
    public String threshold() {
        return "Price +0.5% in 30 s with > 60% of buying from one trader";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public List<Finding> inspect(EngineEvent event, ActivityView activity) {
        if (!(event instanceof TradeExecuted e)) {
            return List.of();
        }
        Trade t = e.trade();
        String buyer = t.aggressorSide() == Side.BUY ? t.buyerId() : null;
        Deque<Print> window = prints.add(t.symbol(), new Print(t.timestamp(), t.price(), t.qty(), buyer, t.buyOrderId()));
        if (window.size() < MIN_TRADES) {
            return List.of();
        }
        long low = window.stream().mapToLong(Print::price).min().orElseThrow();
        long rise = Ticks.changeBp(low, t.price());
        if (rise < MIN_RISE_BP) {
            return List.of();
        }
        Map<String, Long> bought = new HashMap<>();
        long total = 0;
        for (Print p : window) {
            if (p.aggressiveBuyer() != null) {
                bought.merge(p.aggressiveBuyer(), p.qty(), Long::sum);
                total += p.qty();
            }
        }
        if (total == 0) {
            return List.of();
        }
        Map.Entry<String, Long> top = bought.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
        long share = top.getValue() * 100 / total;
        if (share <= MIN_SHARE_PERCENT || !cooldown.tryReport(t.symbol(), t.timestamp())) {
            return List.of();
        }
        Set<Long> flagged = window.stream().filter(p -> top.getKey().equals(p.aggressiveBuyer()))
                .map(Print::buyOrderId).collect(Collectors.toSet());
        Map<String, String> figures = new LinkedHashMap<>();
        figures.put("Share of buying", share + "%");
        figures.put("Trades", String.valueOf(window.size()));
        figures.put("Price move", "+" + rise / 100 + "." + String.format("%02d", rise % 100) + "%");
        figures.put("From", Money.format(low));
        figures.put("To", Money.format(t.price()));
        prints.clear(t.symbol());
        return List.of(new Finding(top.getKey(), t.symbol(),
                "+" + rise / 100 + "." + String.format("%02d", rise % 100) + "% in 30 s · " + share + "% of buying from one trader",
                figures, flagged, new String[] {"Buys at every higher price.", "Nobody else is buying."}));
    }
}
