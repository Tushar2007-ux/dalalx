package com.dalalx.core.bots;

import java.util.List;
import java.util.OptionalLong;

/**
 * What a bot is allowed to see of the market: prices, never the book's insides
 * or other traders' orders.
 *
 * <p>Bots depend on this small interface rather than on the exchange, so a test
 * can hand a bot a fake market with exactly the prices it needs
 * (Dependency Inversion: high-level policy depends on an abstraction).
 */
public interface MarketView {

    /** @return the symbols that can be traded */
    List<String> symbols();

    /** @return the price the day is measured against (yesterday's close in a real market) */
    long referencePrice(String symbol);

    /** @return the last traded price, or the reference price before the first trade */
    long lastPrice(String symbol);

    /** @return the highest bid, if any */
    OptionalLong bestBid(String symbol);

    /** @return the lowest ask, if any */
    OptionalLong bestAsk(String symbol);

    /** @return up to {@code n} most recent trade prices, oldest first */
    List<Long> recentPrices(String symbol, int n);
}
