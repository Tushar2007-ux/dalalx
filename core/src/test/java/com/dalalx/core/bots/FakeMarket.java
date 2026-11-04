package com.dalalx.core.bots;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/** A {@link MarketView} whose prices a test sets by hand. */
final class FakeMarket implements MarketView {

    private final Map<String, Long> reference = new LinkedHashMap<>();
    private final Map<String, List<Long>> prices = new LinkedHashMap<>();

    FakeMarket with(String symbol, long referencePrice, long... history) {
        reference.put(symbol, referencePrice);
        List<Long> list = new ArrayList<>();
        for (long p : history) {
            list.add(p);
        }
        prices.put(symbol, list);
        return this;
    }

    @Override
    public List<String> symbols() {
        return List.copyOf(reference.keySet());
    }

    @Override
    public long referencePrice(String symbol) {
        return reference.get(symbol);
    }

    @Override
    public long lastPrice(String symbol) {
        List<Long> list = prices.get(symbol);
        return list.isEmpty() ? reference.get(symbol) : list.get(list.size() - 1);
    }

    @Override
    public OptionalLong bestBid(String symbol) {
        return OptionalLong.empty();
    }

    @Override
    public OptionalLong bestAsk(String symbol) {
        return OptionalLong.empty();
    }

    @Override
    public List<Long> recentPrices(String symbol, int n) {
        List<Long> list = prices.get(symbol);
        return List.copyOf(list.subList(Math.max(0, list.size() - n), list.size()));
    }
}
