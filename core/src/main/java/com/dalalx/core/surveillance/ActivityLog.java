package com.dalalx.core.surveillance;

import com.dalalx.core.events.OrderAccepted;
import com.dalalx.core.events.OrderCancelled;
import com.dalalx.core.model.Trade;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The surveillance desk's memory: the lives of the most recent orders, so a
 * rule that spots a pattern can attach the evidence (every order the trader
 * placed in the last 30 seconds and what became of it).
 *
 * <p>Bounded: once it holds {@value #CAPACITY} orders, the oldest is forgotten
 * (a {@link LinkedHashMap} in insertion order with {@code removeEldestEntry}).
 * Not thread-safe on its own; the {@link SurveillanceDesk} serialises access.
 */
final class ActivityLog implements ActivityView {

    static final int CAPACITY = 50_000;

    private final Map<Long, OrderLife> lives = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, OrderLife> eldest) {
            return size() > CAPACITY;
        }
    };

    OrderLife placed(OrderAccepted e) {
        var o = e.order();
        OrderLife life = new OrderLife(o.id(), o.traderId(), o.symbol(), o.side(), o.limitPrice(), o.originalQty(),
                e.timestamp(), null, OrderLife.Outcome.LIVE, 0);
        lives.put(o.id(), life);
        return life;
    }

    /** @return the cancelled order's life, or {@code null} if it is too old to be remembered */
    OrderLife cancelled(OrderCancelled e) {
        return lives.computeIfPresent(e.order().id(), (id, life) -> life.cancel(e.timestamp()));
    }

    void traded(Trade t) {
        lives.computeIfPresent(t.buyOrderId(), (id, life) -> life.fill(t.qty(), t.timestamp()));
        lives.computeIfPresent(t.sellOrderId(), (id, life) -> life.fill(t.qty(), t.timestamp()));
    }

    @Override
    public OrderLife life(long orderId) {
        return lives.get(orderId);
    }

    @Override
    public List<OrderLife> of(String traderId, String symbol, Instant since) {
        List<OrderLife> out = new ArrayList<>();
        for (OrderLife life : lives.values()) {
            if (life.traderId().equals(traderId) && life.symbol().equals(symbol) && !life.placedAt().isBefore(since)) {
                out.add(life);
            }
        }
        return out;
    }
}
