package com.uncross.core.matching;

import com.uncross.core.model.Order;
import com.uncross.core.model.Side;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where stop-loss orders wait for their trigger price, out of sight of the
 * order book.
 *
 * <p>A SELL stop at ₹95 means "if the price falls to ₹95 or below, sell me
 * out". A BUY stop at ₹105 means "if it rises to ₹105 or above, buy" (used to
 * cap a loss on a short position, or to jump on a breakout). Neither is in the
 * book: nobody can see or trade against a stop until it fires.
 *
 * <p>Stops are kept in arrival order, so when one price move triggers several
 * of them, the one placed first fires first: price-time priority again, with
 * the trigger standing in for the price.
 *
 * <p><b>Thread confinement:</b> like the order book, a StopBook belongs to one
 * symbol's engine and is only touched by that symbol's worker thread, so it
 * needs no locks.
 */
public final class StopBook {

    private final Map<Long, Order> waiting = new LinkedHashMap<>();

    /** Parks a stop until its trigger is reached. */
    public void park(Order stop) {
        waiting.put(stop.id(), stop);
    }

    /** True if this stop would fire at {@code lastPrice}. */
    public static boolean triggeredAt(Order stop, long lastPrice) {
        if (lastPrice <= 0) {
            return false; // nothing has traded yet, so there is no price to compare
        }
        return stop.side() == Side.SELL ? lastPrice <= stop.limitPrice() : lastPrice >= stop.limitPrice();
    }

    /** Takes out, in arrival order, every stop that fires at {@code lastPrice}. */
    public List<Order> takeTriggered(long lastPrice) {
        List<Order> fired = new ArrayList<>();
        for (Iterator<Order> it = waiting.values().iterator(); it.hasNext(); ) {
            Order stop = it.next();
            if (triggeredAt(stop, lastPrice)) {
                fired.add(stop);
                it.remove();
            }
        }
        return fired;
    }

    public Optional<Order> remove(long orderId) {
        return Optional.ofNullable(waiting.remove(orderId));
    }

    public Optional<Order> find(long orderId) {
        return Optional.ofNullable(waiting.get(orderId));
    }

    public List<Long> orderIdsOf(String traderId) {
        return waiting.values().stream().filter(o -> o.traderId().equals(traderId)).map(Order::id).toList();
    }

    /** Every stop still waiting, oldest first (for the trader's own view). */
    public List<Order> waiting() {
        return List.copyOf(waiting.values());
    }

    public int size() {
        return waiting.size();
    }
}
