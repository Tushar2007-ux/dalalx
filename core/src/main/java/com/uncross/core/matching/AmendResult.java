package com.uncross.core.matching;

import com.uncross.core.model.Order;
import java.util.Optional;

/**
 * What happened when a trader asked to change a resting order.
 *
 * <p>Exchanges treat two kinds of change differently, and so does Uncross:
 * <ul>
 *   <li><b>Smaller size, same price:</b> the order is changed <em>in place</em>
 *       and keeps its place in the queue ({@code keptPriority} is true). Asking
 *       for less never hurts anyone waiting behind you.</li>
 *   <li><b>New price, or more shares:</b> the old order is cancelled and a new
 *       one is entered in its place ("cancel/replace"). It gets a new id and
 *       goes to the back of the queue, because otherwise you could hold your
 *       place in line while changing what you are in line for.</li>
 * </ul>
 *
 * @param orderId      the order the trader asked to change
 * @param amended      false if the order was unknown or already finished
 * @param keptPriority true for an in-place reduction
 * @param order        the order now standing (the same one, or its replacement)
 * @param replacement  the replacement's match result, if it was cancel/replace
 * @param reason       why nothing changed, when {@code amended} is false
 */
public record AmendResult(long orderId, boolean amended, boolean keptPriority, Order order,
                          MatchResult replacement, String reason) {

    static AmendResult inPlace(Order order) {
        return new AmendResult(order.id(), true, true, order, null, null);
    }

    static AmendResult replaced(long oldId, MatchResult replacement) {
        return new AmendResult(oldId, true, false, replacement.incoming(), replacement, null);
    }

    static AmendResult refused(long orderId, Order order, String reason) {
        return new AmendResult(orderId, false, false, order, null, reason);
    }

    public Optional<MatchResult> replacementResult() {
        return Optional.ofNullable(replacement);
    }
}
