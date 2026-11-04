package com.dalalx.core.matching;

import com.dalalx.core.book.OrderBook;
import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Trade;
import java.time.Clock;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Matching {@link MatchingStrategy} for MARKET orders: "fill me now, at
 * whatever the best prices are".
 *
 * <ul>
 *   <li>A market order has no price of its own, so it accepts every resting
 *       price and walks down the book level by level.</li>
 *   <li>It <b>never rests</b>: there's no price to rest at. If the book runs
 *       out before it is filled, the remainder is <em>cancelled</em>.</li>
 *   <li>If the opposite side is completely empty, it can't trade at all, so it
 *       is <em>rejected</em> (it never traded, so REJECTED rather than
 *       CANCELLED).</li>
 * </ul>
 *
 * <p>This is why real traders worry about market orders in a thin book: a large
 * one can "walk the book" and fill far from the last price. The Panic Seller
 * bot uses market orders, and that's what lets it crash a price.
 */
public final class MarketOrderMatcher extends AbstractOrderMatcher {

    static final String NO_LIQUIDITY = "no resting orders on the other side";
    static final String BOOK_EXHAUSTED = "book ran out of liquidity; remainder cancelled";

    /**
     * @param tradeIds source of unique trade ids
     * @param clock    source of trade timestamps
     */
    public MarketOrderMatcher(LongSupplier tradeIds, Clock clock) {
        super(tradeIds, clock);
    }

    @Override
    protected OrderType handledType() {
        return OrderType.MARKET;
    }

    /** A market order takes any price the book offers. */
    @Override
    protected boolean acceptsPrice(Order incoming, long restingPrice) {
        return true;
    }

    @Override
    protected MatchResult finish(Order incoming, OrderBook book, List<Trade> trades) {
        if (trades.isEmpty()) {
            incoming.reject();
            return new MatchResult(incoming, trades, false, NO_LIQUIDITY);
        }
        return cancelRemainder(incoming, trades, BOOK_EXHAUSTED);
    }
}
