package com.dalalx.server.api;

import com.dalalx.core.breaker.MarketPhase;
import com.dalalx.core.events.OrderView;
import com.dalalx.core.matching.CancelResult;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Instant;
import java.util.List;

/**
 * The JSON shapes the REST API sends and receives.
 *
 * <p>These are <em>Data Transfer Objects</em>: plain records that exist only
 * to be turned into JSON. They keep the web contract separate from the core
 * classes. For example, the core {@code Order} is a mutable entity owned by a
 * worker thread and must never be serialised directly; the response carries
 * an immutable {@link OrderView} copy instead.
 */
public final class Dtos {

    private Dtos() {
    }

    /**
     * What the browser POSTs to place an order. Prices are in paise.
     *
     * @param traderId optional; defaults to {@code "YOU"} (the person at the screen)
     */
    public record PlaceOrder(String symbol, String traderId, Side side, OrderType type, long price, long qty) {
    }

    /** The answer to a placed order. */
    public record OrderResult(OrderView order, List<Trade> trades, boolean rested, String reason) {

        static OrderResult of(MatchResult result) {
            return new OrderResult(OrderView.of(result.incoming()), result.trades(), result.rested(), result.reason());
        }
    }

    /** The answer to a cancel. {@code order} is null when the id was unknown. */
    public record CancelResponse(long orderId, boolean cancelled, OrderView order, String reason) {

        static CancelResponse of(CancelResult result) {
            return new CancelResponse(result.orderId(), result.cancelled(),
                    result.knownOrder().map(OrderView::of).orElse(null), result.reason());
        }
    }

    /** One listed stock with its latest price (paise). */
    /** A listed stock with its last price and current trading phase (reason/until null when open). */
    public record SymbolInfo(String symbol, String name, long referencePrice, long lastPrice,
                             MarketPhase phase, String reason, Instant until, Instant since) {
    }

    /** Error body: {@code {"error": "..."}}. */
    public record ApiError(String error) {
    }
}
