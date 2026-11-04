package com.dalalx.core.matching;

import com.dalalx.core.model.OrderRequest;
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
}
