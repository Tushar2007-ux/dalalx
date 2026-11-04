package com.dalalx.core.bots;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.book.LevelSnapshot;
import com.dalalx.core.events.BookChanged;
import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.EngineListener;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.exchange.ListedStock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link MarketView} kept up to date by listening to the engine's events
 * (Observer pattern), so bots read prices without asking the worker threads.
 *
 * <p><b>Thread safety:</b> events arrive on the five worker threads while bots
 * read on the bot thread. Best prices live in a {@link ConcurrentHashMap} of
 * immutable {@code Quote} records (replaced whole, never edited), and each
 * symbol's price history is a deque guarded by its own lock.
 */
public final class LiveMarketView implements MarketView, EngineListener {

    /** How many recent trade prices to remember per symbol. */
    static final int HISTORY = 100;

    private record Quote(OptionalLong bid, OptionalLong ask) {
    }

    private final Map<String, Long> reference = new LinkedHashMap<>();
    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> prices = new ConcurrentHashMap<>();

    /** @param stocks the listed stocks and their reference prices */
    public LiveMarketView(List<ListedStock> stocks) {
        for (ListedStock stock : stocks) {
            reference.put(stock.symbol(), stock.referencePrice());
            prices.put(stock.symbol(), new ArrayDeque<>());
        }
    }

    @Override
    public void onEvent(EngineEvent event) {
        if (event instanceof TradeExecuted e) {
            recordTrade(e.symbol(), e.trade().price());
        } else if (event instanceof BookChanged e) {
            quotes.put(e.symbol(), quoteOf(e.book()));
        }
    }

    /** Remembers one trade price (public so tests and scenarios can set the scene). */
    public void recordTrade(String symbol, long price) {
        Deque<Long> history = prices.get(symbol);
        if (history == null) {
            return;
        }
        synchronized (history) {
            history.addLast(price);
            if (history.size() > HISTORY) {
                history.removeFirst();
            }
        }
    }

    private static Quote quoteOf(BookSnapshot book) {
        return new Quote(best(book.bids()), best(book.asks()));
    }

    private static OptionalLong best(List<LevelSnapshot> side) {
        return side.isEmpty() ? OptionalLong.empty() : OptionalLong.of(side.get(0).price());
    }

    @Override
    public List<String> symbols() {
        return List.copyOf(reference.keySet());
    }

    @Override
    public long referencePrice(String symbol) {
        Long price = reference.get(symbol);
        if (price == null) {
            throw new IllegalArgumentException("unknown symbol " + symbol);
        }
        return price;
    }

    @Override
    public long lastPrice(String symbol) {
        Deque<Long> history = prices.get(symbol);
        if (history != null) {
            synchronized (history) {
                if (!history.isEmpty()) {
                    return history.getLast();
                }
            }
        }
        return referencePrice(symbol);
    }

    @Override
    public OptionalLong bestBid(String symbol) {
        Quote quote = quotes.get(symbol);
        return quote == null ? OptionalLong.empty() : quote.bid();
    }

    @Override
    public OptionalLong bestAsk(String symbol) {
        Quote quote = quotes.get(symbol);
        return quote == null ? OptionalLong.empty() : quote.ask();
    }

    @Override
    public List<Long> recentPrices(String symbol, int n) {
        Deque<Long> history = prices.get(symbol);
        if (history == null) {
            return List.of();
        }
        synchronized (history) {
            List<Long> all = new ArrayList<>(history);
            return List.copyOf(all.subList(Math.max(0, all.size() - n), all.size()));
        }
    }
}
