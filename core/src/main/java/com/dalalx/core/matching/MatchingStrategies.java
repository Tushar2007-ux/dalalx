package com.dalalx.core.matching;

import com.dalalx.core.model.OrderType;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Looks up the {@link MatchingStrategy} for each {@link OrderType}.
 *
 * <p><b>Why a map instead of a switch:</b> the engine never writes
 * {@code if (type == MARKET) ... else if (type == IOC) ...}. It asks this
 * registry for the strategy and calls it. That's the <b>Open/Closed
 * principle</b>: the engine is <em>closed</em> for modification but
 * <em>open</em> for extension. Adding a new order type, say Fill-or-Kill, means:
 * <ol>
 *   <li>a new enum constant {@code FOK} in {@link OrderType},</li>
 *   <li>a new class {@code FokOrderMatcher},</li>
 *   <li>one {@code put} in {@link #standard(LongSupplier, Clock)}.</li>
 * </ol>
 * The existing matchers and the engine stay untouched.
 *
 * <p>An {@link EnumMap} is used because its keys are enum constants: it's
 * compact and fast, and iterates in declaration order.
 */
public final class MatchingStrategies {

    private final Map<OrderType, MatchingStrategy> byType;

    /**
     * @param byType a strategy for every order type
     * @throws IllegalArgumentException if any order type has no strategy
     */
    public MatchingStrategies(Map<OrderType, MatchingStrategy> byType) {
        Objects.requireNonNull(byType, "byType must not be null");
        EnumMap<OrderType, MatchingStrategy> copy = new EnumMap<>(OrderType.class);
        copy.putAll(byType);
        for (OrderType type : OrderType.values()) {
            if (!copy.containsKey(type)) {
                throw new IllegalArgumentException("no matching strategy for " + type);
            }
        }
        this.byType = copy;
    }

    /**
     * The strategies for LIMIT, MARKET and IOC, sharing one trade-id source.
     *
     * @param tradeIds source of unique trade ids
     * @param clock    source of trade timestamps
     */
    public static MatchingStrategies standard(LongSupplier tradeIds, Clock clock) {
        Map<OrderType, MatchingStrategy> map = new EnumMap<>(OrderType.class);
        map.put(OrderType.LIMIT, new LimitOrderMatcher(tradeIds, clock));
        map.put(OrderType.MARKET, new MarketOrderMatcher(tradeIds, clock));
        map.put(OrderType.IOC, new IocOrderMatcher(tradeIds, clock));
        return new MatchingStrategies(map);
    }

    /** @return the strategy that knows how {@code type} orders trade */
    public MatchingStrategy forType(OrderType type) {
        return byType.get(Objects.requireNonNull(type, "type must not be null"));
    }
}
