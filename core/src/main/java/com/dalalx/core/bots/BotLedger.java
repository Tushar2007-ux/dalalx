package com.dalalx.core.bots;

import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.EngineListener;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.model.Trade;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Keeps score for every trader: position, cash, trade count and P&amp;L.
 *
 * <p>It is an {@link EngineListener} (Observer): every {@code TradeExecuted}
 * moves shares from seller to buyer and cash the other way. P&amp;L is
 * "mark-to-market": cash plus each position valued at the latest price.
 *
 * <p><b>Thread safety:</b> trades arrive on the five worker threads and the
 * web server reads scores on its own threads, so both methods are
 * {@code synchronized} on this ledger. Work inside the lock is tiny.
 */
public final class BotLedger implements EngineListener {

    private static final class Account {
        long trades;
        long volume;
        long cash;
        final Map<String, Long> positions = new TreeMap<>();
    }

    private final Map<String, Account> accounts = new HashMap<>();
    private final MarketView market;

    /** @param market supplies last prices for marking positions */
    public BotLedger(MarketView market) {
        this.market = Objects.requireNonNull(market, "market must not be null");
    }

    @Override
    public void onEvent(EngineEvent event) {
        if (event instanceof TradeExecuted e) {
            record(e.trade());
        }
    }

    /** Books one trade for both sides. */
    public synchronized void record(Trade trade) {
        long value = Math.multiplyExact(trade.price(), trade.qty());
        book(trade.buyerId(), trade.symbol(), trade.qty(), -value);
        book(trade.sellerId(), trade.symbol(), -trade.qty(), value);
    }

    private void book(String trader, String symbol, long shares, long cash) {
        Account a = accounts.computeIfAbsent(trader, t -> new Account());
        a.trades++;
        a.volume += Math.abs(shares);
        a.cash += cash;
        a.positions.merge(symbol, shares, Long::sum);
    }

    /** @return the trader's scorecard (all zeros if they have never traded) */
    public synchronized BotStats stats(String traderId) {
        Account a = accounts.get(traderId);
        if (a == null) {
            return new BotStats(traderId, 0, 0, 0, Map.of(), 0);
        }
        long pnl = a.cash;
        for (Map.Entry<String, Long> p : a.positions.entrySet()) {
            pnl += p.getValue() * market.lastPrice(p.getKey());
        }
        return new BotStats(traderId, a.trades, a.volume, a.cash, a.positions, pnl);
    }
}
