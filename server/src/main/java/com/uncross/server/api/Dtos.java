package com.uncross.server.api;

import com.uncross.core.breaker.MarketPhase;
import com.uncross.core.events.AuctionUpdate;
import com.uncross.core.events.OrderView;
import com.uncross.core.matching.AmendResult;
import com.uncross.core.matching.CancelResult;
import com.uncross.core.matching.MatchResult;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import com.uncross.core.model.Trade;
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
    public record PlaceOrder(String symbol, String traderId, Side side, OrderType type, long price, long qty,
                             Long displayQty) {

        /** The usual order, with no iceberg display size. */
        public PlaceOrder(String symbol, String traderId, Side side, OrderType type, long price, long qty) {
            this(symbol, traderId, side, type, price, qty, null);
        }
    }

    /** What an amend sends: the order's new price (paise) and new size. */
    public record AmendOrder(long price, long qty) {
    }

    /** The answer to an amend: {@code order} is the order now standing (the same one, or its replacement). */
    public record AmendResponse(long orderId, boolean amended, boolean keptPriority, OrderView order,
                                List<Trade> trades, String reason) {

        static AmendResponse of(AmendResult result) {
            return new AmendResponse(result.orderId(), result.amended(), result.keptPriority(),
                    result.order() == null ? null : OrderView.of(result.order()),
                    result.replacementResult().map(MatchResult::trades).orElse(List.of()), result.reason());
        }
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
    /** A listed stock with its last price and trading phase (reason/until null when open; auction only during one). */
    public record SymbolInfo(String symbol, String name, long referencePrice, long lastPrice,
                             MarketPhase phase, String reason, Instant until, Instant since, AuctionUpdate auction) {
    }

    /** Error body: {@code {"error": "..."}}. */
    public record ApiError(String error) {
    }
}
