package com.dalalx.core.surveillance;

import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.model.Money;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
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
 * <b>Wash trading:</b> trading with yourself to create volume without any real
 * change of owner, so a stock looks busier than it is.
 *
 * <p>Rule: a "wash pair" is either one trade with the same owner on both sides,
 * or a buy and a sell by the same trader of the same quantity at the same price
 * less than half a second apart. {@value #MIN_PAIRS} pairs in 30 seconds open
 * a case.
 */
public final class WashTradeRule implements SurveillanceRule {

    static final Duration PAIR_GAP = Duration.ofMillis(500);
    static final Duration WINDOW = Duration.ofSeconds(30);
    static final int MIN_PAIRS = 2;

    private record Leg(Instant time, Side side, long price, long qty, long orderId) implements Windows.Timed {
    }

    private record Pair(Instant time, long qty, long buyOrderId, long sellOrderId) implements Windows.Timed {
    }

    private final Windows.Sliding<TraderSymbol, Leg> legs = new Windows.Sliding<>(PAIR_GAP);
    private final Windows.Sliding<TraderSymbol, Pair> pairs = new Windows.Sliding<>(WINDOW);
    private final Windows.Cooldown<TraderSymbol> cooldown = new Windows.Cooldown<>(Duration.ofSeconds(60));

    @Override
    public String id() {
        return "wash";
    }

    @Override
    public String name() {
        return "Wash Trade";
    }

    @Override
    public String threshold() {
        return "Same owner on both sides within 0.5 s";
    }

    @Override
    public Severity severity() {
        return Severity.NOTICE;
    }

    @Override
    public List<Finding> inspect(EngineEvent event, ActivityView activity) {
        if (!(event instanceof TradeExecuted e)) {
            return List.of();
        }
        Trade t = e.trade();
        if (t.buyerId().equals(t.sellerId())) {
            return recordPair(new TraderSymbol(t.buyerId(), t.symbol()), new Pair(t.timestamp(), t.qty(), t.buyOrderId(), t.sellOrderId()));
        }
        List<Finding> found = matchLeg(new TraderSymbol(t.buyerId(), t.symbol()), new Leg(t.timestamp(), Side.BUY, t.price(), t.qty(), t.buyOrderId()));
        return found.isEmpty()
                ? matchLeg(new TraderSymbol(t.sellerId(), t.symbol()), new Leg(t.timestamp(), Side.SELL, t.price(), t.qty(), t.sellOrderId()))
                : found;
    }

    /** Looks for an opposite leg of the same size and price in the last half second. */
    private List<Finding> matchLeg(TraderSymbol key, Leg leg) {
        Deque<Leg> recent = legs.get(key, leg.time());
        for (Leg other : recent) {
            if (other.side() != leg.side() && other.price() == leg.price() && other.qty() == leg.qty()) {
                recent.remove(other);
                long buy = leg.side() == Side.BUY ? leg.orderId() : other.orderId();
                long sell = leg.side() == Side.SELL ? leg.orderId() : other.orderId();
                return recordPair(key, new Pair(leg.time(), leg.qty(), buy, sell));
            }
        }
        legs.add(key, leg);
        return List.of();
    }

    private List<Finding> recordPair(TraderSymbol key, Pair pair) {
        Deque<Pair> window = pairs.add(key, pair);
        if (window.size() < MIN_PAIRS || !cooldown.tryReport(key, pair.time())) {
            return List.of();
        }
        Set<Long> flagged = new HashSet<>();
        long volume = 0;
        for (Pair p : window) {
            flagged.add(p.buyOrderId());
            flagged.add(p.sellOrderId());
            volume += p.qty();
        }
        Map<String, String> figures = new LinkedHashMap<>();
        figures.put("Matched pairs", String.valueOf(window.size()));
        figures.put("Volume created", Money.groupIndian(volume));
        figures.put("Net position change", "0");
        figures.put("Time apart", "< 0.5 s");
        pairs.clear(key);
        return List.of(new Finding(key.traderId(), key.symbol(),
                window.size() + " matched pairs · same owner both sides", figures, flagged,
                new String[] {"Buys from itself.", "Volume with no change of owner."}));
    }
}
