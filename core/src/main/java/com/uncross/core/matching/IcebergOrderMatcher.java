package com.uncross.core.matching;

import com.uncross.core.book.OrderBook;
import com.uncross.core.model.Order;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Trade;
import java.time.Clock;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Matching {@link MatchingStrategy} for ICEBERG orders: a limit order that
 * shows only a slice of its size.
 *
 * <p>Arriving, an iceberg trades exactly like a limit order, using its
 * <em>whole</em> size: hiding size is about what others see waiting, not
 * about what you can take. Whatever is left then rests with just one slice
 * showing. The book does the rest: when that slice is used up, it shows the
 * next one at the back of the queue ({@code PriceLevel.fillOldest}).
 *
 * <p>Big funds use icebergs so the market does not see a huge order coming
 * and move the price away from them. The Whale could use one.
 *
 * <p><b>Pattern:</b> Strategy, and Open/Closed: a new class, no edits to the
 * other matchers.
 */
public final class IcebergOrderMatcher extends AbstractOrderMatcher {

    public IcebergOrderMatcher(LongSupplier tradeIds, Clock clock) {
        super(tradeIds, clock);
    }

    @Override
    protected OrderType handledType() {
        return OrderType.ICEBERG;
    }

    @Override
    protected boolean acceptsPrice(Order incoming, long restingPrice) {
        return withinLimit(incoming, restingPrice);
    }

    @Override
    protected MatchResult finish(Order incoming, OrderBook book, List<Trade> trades) {
        boolean rested = incoming.isActive();
        if (rested) {
            incoming.showNextSlice(); // aggressive fills may have used up the first slice
            book.add(incoming);
        }
        return new MatchResult(incoming, trades, rested);
    }
}
