package com.dalalx.core.xray;

import com.dalalx.core.book.LevelDetail;
import com.dalalx.core.events.OrderView;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import com.dalalx.core.xray.XRay.Fill;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Keeps the X-Rays of the most recent orders sent by chosen traders (by
 * default only the human, "YOU"), ready for the X-Ray page.
 *
 * <p>Built on the worker thread by {@link #record}, read by web threads, so
 * the store is guarded by this object's lock. Bounded to {@value #CAPACITY}.
 */
public final class XRayDesk implements XRayRecorder {

    public static final int CAPACITY = 50;

    private final Set<String> watched;
    private final Narrator narrator;
    private final Deque<XRay> xrays = new ArrayDeque<>();

    /**
     * @param watched trader ids whose orders are X-Rayed
     * @param names   trader id → display name, for the narration
     */
    public XRayDesk(Set<String> watched, Function<String, String> names) {
        this.watched = Set.copyOf(watched);
        this.narrator = new Narrator(names);
    }

    @Override
    public boolean wants(OrderRequest request) {
        return watched.contains(request.traderId());
    }

    @Override
    public void record(OrderView arrived, List<LevelDetail> bookBefore, MatchResult result, Instant time) {
        XRay xray = build(narrator, arrived, bookBefore, result, time);
        synchronized (this) {
            xrays.addFirst(xray);
            if (xrays.size() > CAPACITY) {
                xrays.removeLast();
            }
        }
    }

    /** Turns the raw facts into an {@link XRay} (package-private for tests). */
    static XRay build(Narrator narrator, OrderView o, List<LevelDetail> before, MatchResult result, Instant time) {
        List<Fill> fills = result.trades().stream().map(t -> fillOf(o.side(), t, before)).toList();
        long remaining = result.incoming().remainingQty();
        var facts = new Narrator.Facts(o.symbol(), o.side(), o.type(), o.limitPrice(), o.originalQty(), before,
                fills, result.status(), remaining, result.reason());
        return new XRay(o.id(), o.symbol(), o.traderId(), o.side(), o.type(), o.limitPrice(), o.originalQty(), time,
                before, fills, result.status(), remaining, result.reason(), narrator.narrate(facts));
    }

    private static Fill fillOf(Side side, Trade t, List<LevelDetail> before) {
        boolean buying = side == Side.BUY;
        int level = 0;
        while (level < before.size() && before.get(level).price() != t.price()) {
            level++;
        }
        return new Fill(t.price(), t.qty(), buying ? t.sellerId() : t.buyerId(),
                buying ? t.sellOrderId() : t.buyOrderId(), level);
    }

    /** @return the most recent X-Ray of this trader's orders */
    public synchronized Optional<XRay> latest(String traderId) {
        return xrays.stream().filter(x -> x.traderId().equals(traderId)).findFirst();
    }

    /** @return the X-Ray of one order, if kept */
    public synchronized Optional<XRay> find(long orderId) {
        return xrays.stream().filter(x -> x.orderId() == orderId).findFirst();
    }

    /** @return up to {@code limit} X-Rays of this trader's orders, newest first */
    public synchronized List<XRay> recent(String traderId, int limit) {
        return xrays.stream().filter(x -> x.traderId().equals(traderId)).limit(limit).toList();
    }
}
