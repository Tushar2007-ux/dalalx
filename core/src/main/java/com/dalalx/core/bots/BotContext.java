package com.dalalx.core.bots;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Everything a bot may use when it acts: a read-only view of prices, a gateway
 * for orders, and a random-number source.
 *
 * <p>The random source is passed in (not created inside the bot) so tests can
 * use a seeded generator and get exactly the same decisions every run.
 *
 * @param market what the bot can see
 * @param orders how the bot trades
 * @param random where its dice come from
 */
public record BotContext(MarketView market, OrderGateway orders, RandomGenerator random) {

    public BotContext {
        Objects.requireNonNull(market, "market must not be null");
        Objects.requireNonNull(orders, "orders must not be null");
        Objects.requireNonNull(random, "random must not be null");
    }

    /** @return a uniformly chosen element */
    public <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    /** @return true with the given probability in percent (0–100) */
    public boolean chance(int percent) {
        return random.nextInt(100) < percent;
    }

    /** @return a random quantity in [min, max], rounded down to a multiple of {@code lot} */
    public long qty(long min, long max, long lot) {
        long q = min + random.nextLong(max - min + 1);
        return Math.max(lot, q / lot * lot);
    }
}
