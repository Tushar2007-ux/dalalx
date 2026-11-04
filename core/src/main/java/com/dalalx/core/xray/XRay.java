package com.dalalx.core.xray;

import com.dalalx.core.book.LevelDetail;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import java.time.Instant;
import java.util.List;

/**
 * Everything needed to replay how one order was matched: the order, the
 * opposite side of the book <em>just before</em> it arrived (with whose orders
 * were at each price), the fills it got, how it ended, and a step-by-step
 * narration in plain English. Immutable.
 *
 * @param orderId    the order
 * @param symbol     the stock
 * @param traderId   who sent it
 * @param side       BUY or SELL
 * @param type       LIMIT, MARKET or IOC
 * @param limitPrice its limit in paise (0 for MARKET)
 * @param qty        shares asked for
 * @param time       when it arrived
 * @param bookBefore the side it trades against, best price first, as it was before matching
 * @param fills      what it traded, in order
 * @param status     how it ended
 * @param remaining  shares not filled
 * @param reason     the engine's reason for a rejection or cancellation, or {@code null}
 * @param steps      the narration
 */
public record XRay(long orderId, String symbol, String traderId, Side side, OrderType type, long limitPrice,
                   long qty, Instant time, List<LevelDetail> bookBefore, List<Fill> fills, OrderStatus status,
                   long remaining, String reason, List<Step> steps) {

    public XRay {
        bookBefore = List.copyOf(bookBefore);
        fills = List.copyOf(fills);
        steps = List.copyOf(steps);
    }

    /**
     * One trade the order made.
     *
     * @param price           the resting order's price (trades happen at the resting price)
     * @param qty             shares traded
     * @param counterparty    whose resting order it met
     * @param restingOrderId  that order's id
     * @param level           index of the level in {@code bookBefore} (0 = best)
     */
    public record Fill(long price, long qty, String counterparty, long restingOrderId, int level) {
    }

    /** What a narration step describes; the X-Ray page draws each kind differently. */
    public enum StepKind { ARRIVE, CHECK, FILL, STOP, OUTCOME }

    /**
     * One beat of the explanation.
     *
     * @param kind  what it describes
     * @param title a short heading
     * @param body  one or two sentences
     */
    public record Step(StepKind kind, String title, String body) {
    }

    /** @return shares that traded */
    public long filled() {
        return qty - remaining;
    }
}
