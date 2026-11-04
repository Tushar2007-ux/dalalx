package com.dalalx.core.surveillance;

import com.dalalx.core.events.OrderAccepted;
import com.dalalx.core.events.OrderCancelled;
import com.dalalx.core.events.OrderView;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.matching.SymbolEngine;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Instant;

/** Builds engine events by hand, with times in milliseconds after T0. */
final class Events {

    static final Instant T0 = Instant.parse("2026-11-04T09:15:00Z");
    private long seq;

    static Instant at(long millis) {
        return T0.plusMillis(millis);
    }

    private static OrderView order(long id, String trader, Side side, long price, long qty, long remaining, OrderStatus st) {
        return new OrderView(id, "RELIANCE", trader, side, OrderType.LIMIT, price, qty, remaining, st);
    }

    OrderAccepted placed(long millis, long id, String trader, Side side, long price, long qty) {
        return new OrderAccepted("RELIANCE", at(millis), ++seq, order(id, trader, side, price, qty, qty, OrderStatus.NEW));
    }

    OrderCancelled pulled(long millis, long id, String trader, Side side, long price, long qty) {
        return new OrderCancelled("RELIANCE", at(millis), ++seq, order(id, trader, side, price, qty, qty, OrderStatus.CANCELLED),
                SymbolEngine.CANCELLED_BY_TRADER);
    }

    TradeExecuted trade(long millis, String buyer, String seller, Side aggressor, long price, long qty, long buyId, long sellId) {
        return new TradeExecuted("RELIANCE", at(millis), ++seq,
                new Trade(++seq, "RELIANCE", price, qty, buyId, sellId, buyer, seller, aggressor, at(millis)));
    }
}
