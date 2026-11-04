package com.dalalx.core.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class OrderFactoryAndStrategiesTest {

    private static final Instant T0 = Instant.parse("2026-11-04T09:15:00Z");
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

    @Test
    void factoryAssignsIncreasingIdsAndSequencesAndCopiesTheRequest() {
        AtomicLong counter = new AtomicLong();
        OrderFactory factory = new OrderFactory(counter::incrementAndGet, counter::incrementAndGet, clock);
        OrderRequest request = new OrderRequest("TCS", "MU", Side.BUY, OrderType.LIMIT, 410000, 25);

        Order first = factory.create(request);
        Order second = factory.create(request);

        assertThat(second.id()).isGreaterThan(first.id());
        assertThat(second.sequence()).isGreaterThan(first.sequence());
        assertThat(first.timestamp()).isEqualTo(T0);
        assertThat(first.status()).isEqualTo(OrderStatus.NEW);
        assertThat(first).extracting(Order::symbol, Order::traderId, Order::side, Order::type,
                        Order::limitPrice, Order::originalQty)
                .containsExactly("TCS", "MU", Side.BUY, OrderType.LIMIT, 410000L, 25L);
    }

    @Test
    void standardStrategiesCoverEveryOrderType() {
        MatchingStrategies strategies = MatchingStrategies.standard(new AtomicLong()::incrementAndGet, clock);

        assertThat(strategies.forType(OrderType.LIMIT)).isInstanceOf(LimitOrderMatcher.class);
        assertThat(strategies.forType(OrderType.MARKET)).isInstanceOf(MarketOrderMatcher.class);
        assertThat(strategies.forType(OrderType.IOC)).isInstanceOf(IocOrderMatcher.class);
    }

    @Test
    void registryRefusesToStartWithAMissingType() {
        Map<OrderType, MatchingStrategy> partial = new EnumMap<>(OrderType.class);
        partial.put(OrderType.LIMIT, new LimitOrderMatcher(() -> 1, clock));

        assertThatIllegalArgumentException().isThrownBy(() -> new MatchingStrategies(partial))
                .withMessageContaining("MARKET");
    }

    @Test
    void registryIsADefensiveCopy() {
        Map<OrderType, MatchingStrategy> map = new EnumMap<>(OrderType.class);
        map.put(OrderType.LIMIT, new LimitOrderMatcher(() -> 1, clock));
        map.put(OrderType.MARKET, new MarketOrderMatcher(() -> 1, clock));
        map.put(OrderType.IOC, new IocOrderMatcher(() -> 1, clock));
        MatchingStrategies strategies = new MatchingStrategies(map);

        map.clear(); // changing the caller's map must not affect the registry

        assertThat(strategies.forType(OrderType.IOC)).isInstanceOf(IocOrderMatcher.class);
    }
}
