package com.dalalx.core.matching;

import com.dalalx.core.book.OrderBook;
import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Trade;
import java.time.Clock;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Matching {@link MatchingStrategy} for IOC (Immediate-Or-Cancel) orders.
 *
 * <p>An IOC order matches exactly like a LIMIT order (it never trades worse
 * than its limit), but it refuses to wait: whatever can't be filled
 * <em>immediately</em> is cancelled instead of resting in the book.
 *
 * <p>Compare the three types for "BUY 100 @ ₹103" against asks of 50 @ ₹102 and
 * 80 @ ₹105:
 * <ul>
 *   <li>LIMIT: buys 50 @ ₹102; the other 50 rest as a bid at ₹103.</li>
 *   <li>IOC: buys 50 @ ₹102; the other 50 are cancelled.</li>
 *   <li>MARKET (no limit): buys 50 @ ₹102 and 50 @ ₹105.</li>
 * </ul>
 * Traders use IOC to take available liquidity without leaving a visible order
 * behind.
 */
public final class IocOrderMatcher extends AbstractOrderMatcher {

    static final String NOT_IMMEDIATELY_FILLABLE = "IOC remainder could not fill immediately; cancelled";

    /**
     * @param tradeIds source of unique trade ids
     * @param clock    source of trade timestamps
     */
    public IocOrderMatcher(LongSupplier tradeIds, Clock clock) {
        super(tradeIds, clock);
    }

    @Override
    protected OrderType handledType() {
        return OrderType.IOC;
    }

    @Override
    protected boolean acceptsPrice(Order incoming, long restingPrice) {
        return withinLimit(incoming, restingPrice);
    }

    /** An IOC never rests: any remainder is cancelled. */
    @Override
    protected MatchResult finish(Order incoming, OrderBook book, List<Trade> trades) {
        return cancelRemainder(incoming, trades, NOT_IMMEDIATELY_FILLABLE);
    }
}
