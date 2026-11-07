package com.dalalx.core.bots;

import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.EngineListener;
import com.dalalx.core.events.OrderAccepted;
import com.dalalx.core.events.OrderRejected;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.model.Trade;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Keeps score for every trader: position, cash, trade count and P&amp;L.
 *
 * <p>It is an {@link EngineListener} (Observer): it counts each trader's
 * orders, and every {@code TradeExecuted}
 * moves shares from seller to buyer and cash the other way. P&amp;L is
 * "mark-to-market": cash plus each position valued at the latest price.
 *
 * <p>It also keeps an <em>average cost</em> per position, for the portfolio
 * view ("50 RELIANCE bought at ₹2,450"): buying more adds to the cost,
 * selling part of a position removes that share of the cost, and a trade
 * that flips a position from long to short starts a fresh cost at the
 * trade price.
 *
 * <p><b>Thread safety:</b> trades arrive on the five worker threads and the
 * web server reads scores on its own threads, so both methods are
 * {@code synchronized} on this ledger. Work inside the lock is tiny.
 */
public final class BotLedger implements EngineListener {

    private static final class Account {
        long orders;
        long trades;
        long volume;
        long cash;
        final Map<String, Long> positions = new TreeMap<>();
        /** Paise paid for the shares still held, per symbol (negative for a short). */
        final Map<String, Long> costs = new TreeMap<>();
    }

    private final Map<String, Account> accounts = new HashMap<>();
    private final MarketView market;

    /** @param market supplies last prices for marking positions */
    public BotLedger(MarketView market) {
        this.market = Objects.requireNonNull(market, "market must not be null");
    }

    @Override
    public void onEvent(EngineEvent event) {
        switch (event) {
            case TradeExecuted e -> record(e.trade());
            case OrderAccepted e -> countOrder(e.order().traderId());
            case OrderRejected e -> countOrder(e.order().traderId());
            default -> { }
        }
    }

    private synchronized void countOrder(String trader) {
        accounts.computeIfAbsent(trader, t -> new Account()).orders++;
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
        long before = a.positions.getOrDefault(symbol, 0L);
        a.costs.put(symbol, costAfter(before, a.costs.getOrDefault(symbol, 0L), shares, Math.abs(cash) / Math.abs(shares)));
        a.positions.merge(symbol, shares, Long::sum);
    }

    /**
     * The cost of a position after a trade of {@code shares} at {@code price}.
     *
     * <ul>
     *   <li>Opening or adding (same direction): the trade's value is added.</li>
     *   <li>Reducing: the cost shrinks in proportion to the shares closed, so the
     *       average price of what's left doesn't change.</li>
     *   <li>Flipping (e.g. long 10, sell 15): the old position is closed and the
     *       new 5-share short starts at the trade price.</li>
     * </ul>
     */
    static long costAfter(long position, long cost, long shares, long price) {
        long after = position + shares;
        if (position == 0 || Long.signum(position) == Long.signum(shares)) {
            return cost + shares * price;
        }
        if (after == 0) {
            return 0;
        }
        if (Long.signum(after) == Long.signum(position)) {
            return Math.round(cost * (after / (double) position)); // keep the same average
        }
        return after * price; // flipped: the remainder was opened at this price
    }

    /**
     * @return the trader's open positions (non-zero only), in symbol order,
     *         with average price and profit valued at the latest price
     */
    public synchronized List<Holding> holdings(String traderId) {
        Account a = accounts.get(traderId);
        List<Holding> out = new ArrayList<>();
        if (a == null) {
            return out;
        }
        a.positions.forEach((symbol, qty) -> {
            if (qty != 0) {
                long cost = a.costs.getOrDefault(symbol, 0L);
                long last = market.lastPrice(symbol);
                long value = qty * last;
                out.add(new Holding(symbol, qty, Math.abs(cost / qty), last, value, value - cost));
            }
        });
        return out;
    }

    /** @return the trader's scorecard (all zeros if they have never traded) */
    public synchronized BotStats stats(String traderId) {
        Account a = accounts.get(traderId);
        if (a == null) {
            return new BotStats(traderId, 0, 0, 0, 0, Map.of(), 0);
        }
        long pnl = a.cash;
        for (Map.Entry<String, Long> p : a.positions.entrySet()) {
            pnl += p.getValue() * market.lastPrice(p.getKey());
        }
        return new BotStats(traderId, a.orders, a.trades, a.volume, a.cash, a.positions, pnl);
    }
}
