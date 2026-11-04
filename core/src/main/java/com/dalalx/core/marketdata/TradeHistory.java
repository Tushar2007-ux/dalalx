package com.dalalx.core.marketdata;

import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.EngineListener;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.model.Trade;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the most recent trades of each symbol: for the trade tape when a
 * browser first connects, and for the last traded price.
 *
 * <p>An <b>Observer</b> of {@link TradeExecuted} events. Each symbol keeps at
 * most {@code capacity} trades in a deque: new trades go on the front, and the
 * oldest fall off the back. That's a fixed-size "ring" of history, so memory
 * can't grow without limit.
 *
 * <p>Each symbol's deque is locked separately (written by that symbol's worker
 * thread, read by web threads).
 */
public final class TradeHistory implements EngineListener {

    public static final int DEFAULT_CAPACITY = 500;

    private final int capacity;
    private final Map<String, Deque<Trade>> bySymbol = new ConcurrentHashMap<>();

    public TradeHistory() {
        this(DEFAULT_CAPACITY);
    }

    public TradeHistory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
    }

    @Override
    public void onEvent(EngineEvent event) {
        if (event instanceof TradeExecuted executed) {
            add(executed.trade());
        }
    }

    public void add(Trade trade) {
        Deque<Trade> trades = bySymbol.computeIfAbsent(trade.symbol(), s -> new ArrayDeque<>());
        synchronized (trades) {
            trades.addFirst(trade);
            if (trades.size() > capacity) {
                trades.removeLast();
            }
        }
    }

    /** @return up to {@code limit} most recent trades, newest first */
    public List<Trade> recent(String symbol, int limit) {
        Deque<Trade> trades = bySymbol.get(symbol);
        if (trades == null) {
            return List.of();
        }
        synchronized (trades) {
            return new ArrayList<>(trades).stream().limit(limit).toList();
        }
    }

    /** @return the price of the most recent trade, if there has been one */
    public OptionalLong lastPrice(String symbol) {
        List<Trade> last = recent(symbol, 1);
        return last.isEmpty() ? OptionalLong.empty() : OptionalLong.of(last.getFirst().price());
    }
}
