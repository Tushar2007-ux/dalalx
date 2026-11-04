package com.dalalx.core.events;

import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;

/**
 * An immutable copy of an {@link Order} at one moment, for putting inside events.
 *
 * <p>Why not put the {@code Order} itself in an event? {@code Order} is a
 * mutable entity confined to its symbol's worker thread. Events are read by
 * other threads (the server, surveillance, headlines), so they must only carry
 * values that can never change. This record is that frozen picture: by the time
 * a listener reads it, the real order may have filled further, but the event
 * still describes exactly what happened at that moment.
 *
 * @param id           order id
 * @param symbol       stock symbol
 * @param traderId     who placed it
 * @param side         BUY or SELL
 * @param type         LIMIT, MARKET or IOC
 * @param limitPrice   limit price in paise (0 for MARKET)
 * @param originalQty  shares originally ordered
 * @param remainingQty shares not yet filled at this moment
 * @param status       status at this moment
 */
public record OrderView(long id, String symbol, String traderId, Side side, OrderType type,
                        long limitPrice, long originalQty, long remainingQty, OrderStatus status) {

    /** @return a frozen copy of {@code order}'s current state */
    public static OrderView of(Order order) {
        return new OrderView(order.id(), order.symbol(), order.traderId(), order.side(), order.type(),
                order.limitPrice(), order.originalQty(), order.remainingQty(), order.status());
    }

    /** @return shares filled at this moment */
    public long filledQty() {
        return originalQty - remainingQty;
    }
}
