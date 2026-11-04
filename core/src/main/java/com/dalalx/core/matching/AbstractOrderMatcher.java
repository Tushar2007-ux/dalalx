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
 * Shared skeleton for every order-type {@link MatchingStrategy}.
 *
 * <p><b>Template Method pattern:</b> all order types match the same way: walk
 * the opposite side of the book best-price-first, trading at each resting
 * order's price, for as long as the price is acceptable. They differ in only
 * two small decisions, which subclasses fill in:
 * <ol>
 *   <li>{@link #acceptsPrice(Order, long)}: is this resting price good enough?
 *       A LIMIT or IOC order checks its limit; a MARKET order accepts any price.</li>
 *   <li>{@link #finish(Order, OrderBook, List)}: what happens to an unfilled
 *       remainder? LIMIT rests it in the book; MARKET and IOC cancel it.</li>
 * </ol>
 * The fixed algorithm lives here once, in the {@code final} method
 * {@link #match(Order, OrderBook)}, so it can't be copied wrongly into three
 * places. Strategy (which matcher?) and Template Method (how does each matcher
 * vary?) work together here.
 *
 * <p>Trades always execute at the <em>resting</em> order's price. The resting
 * order showed that price first, and the incoming order accepted it.
 *
 * <p>Like the book, a matcher is used only by its symbol's worker thread.
 */
public abstract class AbstractOrderMatcher implements MatchingStrategy {

    private final LongSupplier tradeIds;
    private final Clock clock;

    /**
     * @param tradeIds source of unique trade ids
     * @param clock    source of trade timestamps (a fixed clock in tests)
     */
    protected AbstractOrderMatcher(LongSupplier tradeIds, Clock clock) {
        this.tradeIds = Objects.requireNonNull(tradeIds, "tradeIds must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** @return the single order type this matcher handles */
    protected abstract OrderType handledType();

    /**
     * Decides whether the incoming order is willing to trade at a resting price.
     *
     * @param incoming     the aggressive order
     * @param restingPrice price of the best order on the opposite side, in paise
     */
    protected abstract boolean acceptsPrice(Order incoming, long restingPrice);

    /**
     * Decides what happens once matching stops: rest, cancel or reject the
     * remainder, and build the result.
     *
     * @param incoming the order after matching (may still have quantity left)
     * @param book     the order book
     * @param trades   trades executed so far, best price first
     */
    protected abstract MatchResult finish(Order incoming, OrderBook book, List<Trade> trades);

    /** The fixed matching algorithm. Subclasses customise the hooks, not this. */
    @Override
    public final MatchResult match(Order incoming, OrderBook book) {
        validate(incoming, book);
        List<Trade> trades = new ArrayList<>();
        Side restingSide = incoming.side().opposite();
        while (incoming.isActive() && bestIsAcceptable(incoming, book, restingSide)) {
            trades.add(tradeWithBest(incoming, book, restingSide));
        }
        return finish(incoming, book, trades);
    }

    private void validate(Order incoming, OrderBook book) {
        if (incoming.type() != handledType()) {
            throw new IllegalArgumentException(
                    getClass().getSimpleName() + " only handles " + handledType() + " orders: " + incoming);
        }
        if (!incoming.isActive()) {
            throw new IllegalArgumentException("incoming order is not active: " + incoming);
        }
        if (!incoming.symbol().equals(book.symbol())) {
            throw new IllegalArgumentException("order is for " + incoming.symbol() + ", book is " + book.symbol());
        }
    }

    private boolean bestIsAcceptable(Order incoming, OrderBook book, Side restingSide) {
        return book.peekBest(restingSide)
                .map(resting -> acceptsPrice(incoming, resting.limitPrice()))
                .orElse(false);
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

    /**
     * Helper for subclasses: a buyer accepts any price up to its limit; a seller
     * accepts any price down to its limit.
     */
    protected static boolean withinLimit(Order incoming, long restingPrice) {
        return incoming.side() == Side.BUY
                ? restingPrice <= incoming.limitPrice()
                : restingPrice >= incoming.limitPrice();
    }

    /**
     * Helper for subclasses: cancels whatever is left of {@code incoming} and
     * returns a result explaining why.
     */
    protected static MatchResult cancelRemainder(Order incoming, List<Trade> trades, String reason) {
        if (incoming.isActive()) {
            incoming.cancel();
            return new MatchResult(incoming, trades, false, reason);
        }
        return new MatchResult(incoming, trades, false);
    }
}
