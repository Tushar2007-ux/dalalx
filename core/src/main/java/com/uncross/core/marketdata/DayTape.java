package com.uncross.core.marketdata;

import com.uncross.core.events.EngineEvent;
import com.uncross.core.events.EngineListener;
import com.uncross.core.events.TradeExecuted;
import com.uncross.core.model.Trade;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The day's figures for the end-of-day edition: for each stock its open,
 * high, low, last, volume and turnover, plus the biggest trades of the day.
 *
 * <p>It listens to every trade (Observer) and keeps only running totals, so
 * it stays small however long the day runs, unlike a list of every trade.
 * Events arrive on the five worker threads, so its methods are
 * {@code synchronized}.
 */
public final class DayTape implements EngineListener {

    public record StockDay(String symbol, long open, long high, long low, long last, long volume, long turnover,
                           long trades) {
    }

    public record BigTrade(Instant at, String symbol, long price, long qty, String buyerId, String sellerId) {
        public long notional() {
            return price * qty;
        }
    }

    public record Summary(List<StockDay> stocks, List<BigTrade> biggest, long trades, long turnover) {
    }

    static final int BIGGEST = 5;

    private static final class Running {
        long open;
        long high;
        long low;
        long last;
        long volume;
        long turnover;
        long trades;
    }

    private final Map<String, Running> stocks = new LinkedHashMap<>();
    private final List<BigTrade> biggest = new ArrayList<>();

    @Override
    public void onEvent(EngineEvent event) {
        if (event instanceof TradeExecuted e) {
            add(e.trade());
        }
    }

    public synchronized void add(Trade t) {
        Running r = stocks.computeIfAbsent(t.symbol(), s -> new Running());
        if (r.trades == 0) {
            r.open = t.price();
            r.high = t.price();
            r.low = t.price();
        }
        r.high = Math.max(r.high, t.price());
        r.low = Math.min(r.low, t.price());
        r.last = t.price();
        r.volume += t.qty();
        r.turnover += t.notional();
        r.trades++;
        keepIfBig(new BigTrade(t.timestamp(), t.symbol(), t.price(), t.qty(), t.buyerId(), t.sellerId()));
    }

    /** Keeps the five largest trades by value: a tiny "top five" list, re-sorted as it changes. */
    private void keepIfBig(BigTrade trade) {
        if (biggest.size() == BIGGEST && trade.notional() <= biggest.get(BIGGEST - 1).notional()) {
            return;
        }
        biggest.add(trade);
        biggest.sort(Comparator.comparingLong(BigTrade::notional).reversed());
        if (biggest.size() > BIGGEST) {
            biggest.remove(BIGGEST);
        }
    }

    public synchronized Summary summary() {
        List<StockDay> days = new ArrayList<>();
        long trades = 0;
        long turnover = 0;
        for (Map.Entry<String, Running> e : stocks.entrySet()) {
            Running r = e.getValue();
            days.add(new StockDay(e.getKey(), r.open, r.high, r.low, r.last, r.volume, r.turnover, r.trades));
            trades += r.trades;
            turnover += r.turnover;
        }
        return new Summary(days, List.copyOf(biggest), trades, turnover);
    }
}
