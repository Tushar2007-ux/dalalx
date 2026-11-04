package com.dalalx.core.surveillance;

import java.time.Instant;
import java.util.List;

/** Read-only access to the recent order lives, for rules. */
public interface ActivityView {

    /** @return one order's life (O(1)), or {@code null} if it is unknown or forgotten */
    OrderLife life(long orderId);

    /** @return the trader's orders in this stock placed at or after {@code since}, oldest first */
    List<OrderLife> of(String traderId, String symbol, Instant since);
}
