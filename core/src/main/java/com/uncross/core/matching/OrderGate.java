package com.uncross.core.matching;

import com.uncross.core.model.OrderRequest;
import java.util.List;
import java.util.Optional;

/**
 * A check every order must pass before it may trade, e.g. "is this trader
 * suspended by surveillance?". The engine asks the gate, so it needs to know
 * nothing about surveillance (Dependency Inversion).
 */
@FunctionalInterface
public interface OrderGate {

    /** A gate that lets everything through. */
    OrderGate OPEN = request -> Optional.empty();

    /** @return why this order must be refused, or empty to let it through */
    Optional<String> refusal(OrderRequest request);

    /**
     * Several gates in a row: the first refusal wins, and a later gate is only
     * asked if every earlier one let the order through. Order matters: a gate
     * that sets money aside (the risk desk) should come last, so it never
     * reserves for an order an earlier gate turns away.
     */
    static OrderGate allOf(OrderGate... gates) {
        List<OrderGate> list = List.of(gates);
        return request -> {
            for (OrderGate gate : list) {
                Optional<String> refusal = gate.refusal(request);
                if (refusal.isPresent()) {
                    return refusal;
                }
            }
            return Optional.empty();
        };
    }
}
