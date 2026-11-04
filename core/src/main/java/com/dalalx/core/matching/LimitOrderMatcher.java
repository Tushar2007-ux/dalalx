package com.dalalx.core.matching;

import com.dalalx.core.book.OrderBook;
import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Matching {@link MatchingStrategy} for LIMIT orders, using price-time priority.
 *
 * <p><b>The algorithm:</b>
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
public final class LimitOrderMatcher implements MatchingStrategy {

    private final LongSupplier tradeIds;
    private final Clock clock;

    /**
     * @param tradeIds source of unique trade ids
     * @param clock    source of trade timestamps
     */
    public LimitOrderMatcher(LongSupplier tradeIds, Clock clock) {
        this.tradeIds = Objects.requireNonNull(tradeIds, "tradeIds must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public MatchResult match(Order incoming, OrderBook book) {
        validate(incoming, book);
        List<Trade> trades = new ArrayList<>();
        Side restingSide = incoming.side().opposite();
        while (incoming.isActive() && bestCrosses(incoming, book, restingSide)) {
            trades.add(tradeWithBest(incoming, book, restingSide));
        }
        boolean rested = incoming.isActive(); // anything left over waits in the book
        if (rested) {
            book.add(incoming);
        }
        return new MatchResult(incoming, trades, rested);
    }

    private static void validate(Order incoming, OrderBook book) {
        if (incoming.type() != OrderType.LIMIT) {
            throw new IllegalArgumentException("LimitOrderMatcher only handles LIMIT orders: " + incoming);
        }
        if (!incoming.isActive()) {
            throw new IllegalArgumentException("incoming order is not active: " + incoming);
        }
        if (!incoming.symbol().equals(book.symbol())) {
            throw new IllegalArgumentException("order is for " + incoming.symbol() + ", book is " + book.symbol());
        }
    }

    private static boolean bestCrosses(Order incoming, OrderBook book, Side restingSide) {
        return book.peekBest(restingSide)
                .map(resting -> crosses(incoming, resting.limitPrice()))
                .orElse(false);
    }

    /** A buyer accepts any price up to its limit; a seller accepts any price down to its limit. */
    private static boolean crosses(Order incoming, long restingPrice) {
        return incoming.side() == Side.BUY
                ? restingPrice <= incoming.limitPrice()
                : restingPrice >= incoming.limitPrice();
    }

    /** Executes one trade between {@code incoming} and the best resting order. */
    private Trade tradeWithBest(Order incoming, OrderBook book, Side restingSide) {
        Order resting = book.peekBest(restingSide).orElseThrow();
        long qty = Math.min(incoming.remainingQty(), resting.remainingQty());
        long price = resting.limitPrice(); // trades always execute at the RESTING price
        book.fillBest(restingSide, qty);
        incoming.fill(qty);
        return newTrade(incoming, resting, price, qty);
    }

    private Trade newTrade(Order incoming, Order resting, long price, long qty) {
        Order buy = incoming.side() == Side.BUY ? incoming : resting;
        Order sell = incoming.side() == Side.SELL ? incoming : resting;
        return new Trade(tradeIds.getAsLong(), incoming.symbol(), price, qty,
                buy.id(), sell.id(), buy.traderId(), sell.traderId(),
                incoming.side(), clock.instant());
    }
}
