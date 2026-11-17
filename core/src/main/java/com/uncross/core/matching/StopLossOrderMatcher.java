package com.uncross.core.matching;

import com.uncross.core.book.OrderBook;
import com.uncross.core.model.Order;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Trade;
import java.time.Clock;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Matching {@link MatchingStrategy} for a STOP_LOSS order <em>once it has
 * been triggered</em>: from that moment it behaves exactly like a market order.
 *
 * <p>Waiting for the trigger is not this class's job. Until the price reaches
 * the trigger, the order sits in the engine's {@link StopBook}, out of sight of
 * the order book. The engine hands it to this strategy only when it fires.
 * Splitting the two jobs keeps each class small: one decides <em>when</em>, the
 * other decides <em>how</em>.
 *
 * <p>Unlike a fresh market order, a triggered stop was already accepted when it
 * was parked, so if the book is empty it is <em>cancelled</em>, not rejected.
 *
 * <p><b>Pattern:</b> Strategy, and Open/Closed: this type was added as a new
 * class plus one line in {@link MatchingStrategies#standard}; no existing
 * matcher changed.
 */
public final class StopLossOrderMatcher extends AbstractOrderMatcher {

    static final String NOTHING_TO_HIT = "stop triggered, but the book had nothing to trade against";

    public StopLossOrderMatcher(LongSupplier tradeIds, Clock clock) {
        super(tradeIds, clock);
    }

    @Override
    protected OrderType handledType() {
        return OrderType.STOP_LOSS;
    }

    /** Triggered, it takes any price: that is the point of a stop-loss, and its risk. */
    @Override
    protected boolean acceptsPrice(Order incoming, long restingPrice) {
        return true;
    }

    @Override
    protected MatchResult finish(Order incoming, OrderBook book, List<Trade> trades) {
        return cancelRemainder(incoming, trades, trades.isEmpty() ? NOTHING_TO_HIT : MarketOrderMatcher.BOOK_EXHAUSTED);
    }
}
