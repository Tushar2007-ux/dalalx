package com.dalalx.core.exchange;

/**
 * A stock that trades on the exchange.
 *
 * @param symbol         ticker, e.g. {@code "RELIANCE"}
 * @param name           display name
 * @param referencePrice starting price in paise; bots quote around it and the
 *                       circuit breaker measures moves from it
 */
public record ListedStock(String symbol, String name, long referencePrice) {

    public ListedStock {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (referencePrice <= 0) {
            throw new IllegalArgumentException("referencePrice must be > 0");
        }
    }
}
