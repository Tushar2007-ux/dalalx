package com.dalalx.core.exchange;

/**
 * Thrown when a request names a stock that isn't listed on the exchange.
 *
 * <p>It's a subclass of {@link IllegalArgumentException} (it <em>is</em> a bad
 * argument), but a separate type, so the server can answer "404 Not Found"
 * for an unknown stock and "400 Bad Request" for other invalid input.
 */
public final class UnknownSymbolException extends IllegalArgumentException {

    public UnknownSymbolException(String symbol) {
        super("unknown symbol: " + symbol);
    }
}
