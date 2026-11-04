package com.dalalx.core.surveillance;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traders the Inspector has barred from trading. The exchange's workers ask
 * {@link #refusal} before accepting each order, while the web thread adds and
 * removes names, so the set is a concurrent one.
 */
public final class Suspensions {

    private final Set<String> suspended = ConcurrentHashMap.newKeySet();

    public void suspend(String traderId) {
        suspended.add(traderId);
    }

    /** @return true if the trader was suspended */
    public boolean reinstate(String traderId) {
        return suspended.remove(traderId);
    }

    public boolean isSuspended(String traderId) {
        return suspended.contains(traderId);
    }

    /** @return why this trader's order must be refused, if it must */
    public Optional<String> refusal(String traderId) {
        return isSuspended(traderId) ? Optional.of("trader suspended by market surveillance") : Optional.empty();
    }

    public Set<String> all() {
        return Set.copyOf(suspended);
    }
}
