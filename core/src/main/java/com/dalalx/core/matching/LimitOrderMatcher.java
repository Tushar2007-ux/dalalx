package com.dalalx.core.matching;

import com.dalalx.core.book.OrderBook;
import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Trade;
import java.time.Clock;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Matching {@link MatchingStrategy} for LIMIT orders, using price-time priority.
 *
 * <p><b>The algorithm</b> (the loop itself is in {@link AbstractOrderMatcher}):
 * <ol>
 *   <li>Look at the best order on the opposite side: the lowest ask for a BUY,
 *       the highest bid for a SELL.</li>
 *   <li>If its price <em>crosses</em> the incoming limit (ask &le; buy limit, or
 *       bid &ge; sell limit), trade {@code min(incoming remaining, resting
 *       remaining)} shares <b>at the resting order's price</b>.</li>
 *   <li>Repeat until the incoming order is filled or prices no longer cross.</li>
 *   <li>Any remainder <em>rests</em> in the book as a passive order.</li>
 * </ol>
 *
 * <p>Why the resting price? The resting order was there first and showed that
 * price publicly; the incoming order accepted it by crossing. A buyer bidding
 * ₹105 against an ask of ₹100 pays ₹100. This is called <em>price
 * improvement</em> for the aggressor.
 *
 * <p>Trade ids and timestamps come from an injected {@link LongSupplier} and
 * {@link Clock} (dependency injection), so tests can use fixed, predictable
 * values.
 */
public final class LimitOrderMatcher extends AbstractOrderMatcher {

    /**
     * @param tradeIds source of unique trade ids
     * @param clock    source of trade timestamps
     */
    public LimitOrderMatcher(LongSupplier tradeIds, Clock clock) {
        super(tradeIds, clock);
    }

    @Override
    protected OrderType handledType() {
        return OrderType.LIMIT;
    }

    @Override
    protected boolean acceptsPrice(Order incoming, long restingPrice) {
        return withinLimit(incoming, restingPrice);
    }

    /** A LIMIT order's unfilled remainder waits in the book for a counterparty. */
    @Override
    protected MatchResult finish(Order incoming, OrderBook book, List<Trade> trades) {
        boolean rested = incoming.isActive();
        if (rested) {
            book.add(incoming);
        }
        return new MatchResult(incoming, trades, rested);
    }
}
