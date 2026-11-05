package com.dalalx.core.auction;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.book.LevelSnapshot;
import com.dalalx.core.book.OrderBook;
import com.dalalx.core.model.Order;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.LongSupplier;

/**
 * The call-auction algorithm, the way real exchanges reopen after a halt
 * (NSE's pre-open session works the same way).
 *
 * <p><b>Finding the price ("equilibrium price"):</b> for each candidate price
 * p, buyers willing to pay p or more want D(p) shares and sellers willing to
 * accept p or less offer S(p) shares, so min(D(p), S(p)) can trade. The
 * auction picks the p that trades the <b>most shares</b>. Ties are broken by
 * the <b>smallest leftover imbalance</b> |D − S|, then by the price
 * <b>closest to the reference</b> (the last price before the halt). Only
 * prices that appear in the book need checking, because the volume can only
 * change at those prices.
 *
 * <p><b>Uncrossing:</b> then the crossing orders are matched best price first
 * (and oldest first within a price), exactly as in continuous trading, except
 * that <em>every</em> trade happens at the one auction price. Because that
 * price maximises volume, nothing is left crossed afterwards: a leftover bid
 * above a leftover ask would mean a higher-volume price existed.
 *
 * <p>Pure functions over a book; not thread-safe, like the book itself.
 */
public final class CallAuction {

    private CallAuction() {
    }

    /** @return the indicative price, or empty if no bid and ask overlap (nothing would trade) */
    public static Optional<IndicativePrice> indicative(OrderBook book, long reference) {
        BookSnapshot all = book.snapshot(Integer.MAX_VALUE);
        if (all.bids().isEmpty() || all.asks().isEmpty() || all.bids().get(0).price() < all.asks().get(0).price()) {
            return Optional.empty();
        }
        TreeSet<Long> candidates = new TreeSet<>();
        all.bids().forEach(l -> candidates.add(l.price()));
        all.asks().forEach(l -> candidates.add(l.price()));
        IndicativePrice best = null;
        for (long p : candidates) {
            long demand = all.bids().stream().filter(l -> l.price() >= p).mapToLong(LevelSnapshot::totalQty).sum();
            long supply = all.asks().stream().filter(l -> l.price() <= p).mapToLong(LevelSnapshot::totalQty).sum();
            IndicativePrice here = new IndicativePrice(p, Math.min(demand, supply), demand, supply);
            if (best == null || better(here, best, reference)) {
                best = here;
            }
        }
        return best.volume() > 0 ? Optional.of(best) : Optional.empty();
    }

    private static boolean better(IndicativePrice a, IndicativePrice b, long reference) {
        if (a.volume() != b.volume()) {
            return a.volume() > b.volume();
        }
        if (Math.abs(a.imbalance()) != Math.abs(b.imbalance())) {
            return Math.abs(a.imbalance()) < Math.abs(b.imbalance());
        }
        return Math.abs(a.price() - reference) < Math.abs(b.price() - reference);
    }

    /**
     * Matches every crossing order at {@code price}.
     *
     * @return the trades, in the order they happened
     */
    public static List<Trade> uncross(OrderBook book, long price, LongSupplier tradeIds, Instant time) {
        List<Trade> trades = new ArrayList<>();
        while (book.bestBid().orElse(Long.MIN_VALUE) >= price && book.bestAsk().orElse(Long.MAX_VALUE) <= price) {
            Order bid = book.peekBest(Side.BUY).orElseThrow();
            Order ask = book.peekBest(Side.SELL).orElseThrow();
            long qty = Math.min(bid.remainingQty(), ask.remainingQty());
            book.fillBest(Side.BUY, qty);
            book.fillBest(Side.SELL, qty);
            // No one was the aggressor in an auction; by convention, the side of the later order.
            Side later = bid.sequence() > ask.sequence() ? Side.BUY : Side.SELL;
            trades.add(new Trade(tradeIds.getAsLong(), book.symbol(), price, qty, bid.id(), ask.id(),
                    bid.traderId(), ask.traderId(), later, time));
        }
        return trades;
    }
}
