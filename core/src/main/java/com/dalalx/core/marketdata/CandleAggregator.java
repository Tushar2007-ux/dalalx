package com.dalalx.core.marketdata;

import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.EngineListener;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.model.Trade;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds OHLCV {@link Candle}s for the price chart from the stream of trades.
 *
 * <p>An <b>Observer</b> ({@link EngineListener}): it only listens to
 * {@link TradeExecuted} events, so the matching engine doesn't know charts
 * exist. For every trade it updates the current candle of every
 * {@link CandleInterval} at once (1m, 5m, 15m, 1D).
 *
 * <p><b>Threading:</b> trades for different symbols arrive on different worker
 * threads, while the server reads candles on web threads. Each
 * (symbol, interval) series has its own lock ({@code synchronized} on the
 * series), so readers always see a consistent list, and two symbols never wait
 * for each other.
 *
 * <p>Each series keeps at most {@link #MAX_CANDLES} candles, so memory stays
 * bounded however long the market runs.
 */
public final class CandleAggregator implements EngineListener {

    static final int MAX_CANDLES = 500;

    private record Key(String symbol, CandleInterval interval) {
    }

    private final Map<Key, NavigableMap<Long, Candle>> series = new ConcurrentHashMap<>();

    @Override
    public void onEvent(EngineEvent event) {
        if (event instanceof TradeExecuted executed) {
            add(executed.trade());
        }
    }

    /** Adds one trade to the current candle of every interval. */
    public void add(Trade trade) {
        long millis = trade.timestamp().toEpochMilli();
        for (CandleInterval interval : CandleInterval.values()) {
            NavigableMap<Long, Candle> candles =
                    series.computeIfAbsent(new Key(trade.symbol(), interval), k -> new TreeMap<>());
            long start = interval.bucketStart(millis);
            synchronized (candles) {
                candles.merge(start, Candle.first(start, trade.price(), trade.qty()),
                        (old, ignored) -> old.with(trade.price(), trade.qty()));
                while (candles.size() > MAX_CANDLES) {
                    candles.pollFirstEntry(); // forget the oldest
                }
            }
        }
    }

    /** @return the candles for one symbol and interval, oldest first */
    public List<Candle> candles(String symbol, CandleInterval interval) {
        NavigableMap<Long, Candle> candles = series.get(new Key(symbol, interval));
        if (candles == null) {
            return List.of();
        }
        synchronized (candles) {
            return List.copyOf(new ArrayList<>(candles.values()));
        }
    }
}
