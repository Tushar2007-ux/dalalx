package com.dalalx.core.auction;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.breaker.BreakerRule;
import com.dalalx.core.breaker.MarketPhase;
import com.dalalx.core.breaker.TradingSession;
import com.dalalx.core.events.AuctionUpdate;
import com.dalalx.core.events.EventBus;
import com.dalalx.core.events.MarketStatusChanged;
import com.dalalx.core.events.RecordingListener;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.matching.MatchingStrategies;
import com.dalalx.core.matching.OrderFactory;
import com.dalalx.core.matching.SymbolEngine;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import com.dalalx.core.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Halt → call auction → single-price reopen, inside a real engine. */
class AuctionEngineTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T09:15:00Z"));
    private final AtomicLong ids = new AtomicLong();
    private final EventBus bus = new EventBus();
    private final RecordingListener events = new RecordingListener();
    private final SymbolEngine engine = new SymbolEngine("TCS",
            new OrderFactory(ids::incrementAndGet, ids::incrementAndGet, clock),
            MatchingStrategies.standard(ids::incrementAndGet, clock), bus, clock,
            TradingSession.withBreaker("TCS", BreakerRule.STANDARD));

    AuctionEngineTest() {
        bus.register(events);
    }

    private MatchResult order(String trader, Side side, OrderType type, long price, long qty) {
        return engine.submit(new OrderRequest("TCS", trader, side, type, price, qty));
    }

    private void crashIntoAuction() {
        order("MUNIM", Side.BUY, OrderType.LIMIT, 100_000, 10);
        order("MUNIM", Side.BUY, OrderType.LIMIT, 89_000, 10);
        order("PANIC", Side.SELL, OrderType.MARKET, 0, 20);   // trips the breaker
        clock.advance(Duration.ofSeconds(30));
        engine.tick();                                        // halt over: auction begins
        events.clear();
    }

    @Test
    void theHaltIsFollowedByACallAuction() {
        order("MUNIM", Side.BUY, OrderType.LIMIT, 100_000, 10);
        order("MUNIM", Side.BUY, OrderType.LIMIT, 89_000, 10);
        order("PANIC", Side.SELL, OrderType.MARKET, 0, 20);
        clock.advance(Duration.ofSeconds(30));
        engine.tick();

        assertThat(events.eventsOf(MarketStatusChanged.class)).extracting(MarketStatusChanged::phase)
                .containsExactly(MarketPhase.HALTED, MarketPhase.AUCTION);
        assertThat(events.eventsOf(AuctionUpdate.class)).hasSize(1);
    }

    @Test
    void ordersAreCollectedNotMatchedAndAnIndicativePriceIsPublished() {
        crashIntoAuction();

        assertThat(order("X", Side.BUY, OrderType.MARKET, 0, 5).reason())
                .isEqualTo("only limit orders are accepted during the call auction");
        MatchResult bid = order("WHALE", Side.BUY, OrderType.LIMIT, 92_000, 100);
        MatchResult ask = order("PANIC", Side.SELL, OrderType.LIMIT, 90_000, 60);

        assertThat(bid.rested()).isTrue();
        assertThat(ask.trades()).isEmpty();                  // crosses, but nothing trades yet
        assertThat(events.eventsOf(TradeExecuted.class)).isEmpty();
        AuctionUpdate latest = events.eventsOf(AuctionUpdate.class).getLast();
        assertThat(latest.volume()).isEqualTo(60);
        assertThat(latest.endsAt()).isEqualTo(clock.instant().plusSeconds(30));
    }

    @Test
    void theAuctionEndsWithEveryCrossingOrderTradingAtOnePrice() {
        crashIntoAuction();
        order("WHALE", Side.BUY, OrderType.LIMIT, 92_000, 100);
        order("MOMENTUM", Side.BUY, OrderType.LIMIT, 91_000, 50);
        order("PANIC", Side.SELL, OrderType.LIMIT, 90_000, 60);
        order("MUNIM", Side.SELL, OrderType.LIMIT, 91_000, 70);
        long auctionPrice = events.eventsOf(AuctionUpdate.class).getLast().price();
        events.clear();

        clock.advance(Duration.ofSeconds(30));
        engine.tick();

        assertThat(events.eventsOf(TradeExecuted.class)).extracting(e -> e.trade().price()).containsOnly(auctionPrice);
        assertThat(events.eventsOf(TradeExecuted.class)).extracting(e -> e.trade().qty()).containsExactly(60L, 40L, 30L); // 130 shares at ₹910
        assertThat(events.eventsOf(MarketStatusChanged.class)).extracting(MarketStatusChanged::phase)
                .containsExactly(MarketPhase.OPEN);
        // Left over: 20 of Momentum Chaser's ₹910 bid; every ask traded, so nothing is crossed.
        assertThat(engine.snapshot(5).bids()).extracting(l -> l.price(), l -> l.totalQty())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(91_000L, 20L));
        assertThat(engine.snapshot(5).asks()).isEmpty();
        // Back to continuous trading:
        assertThat(order("X", Side.SELL, OrderType.MARKET, 0, 5).status()).isEqualTo(OrderStatus.FILLED);
    }
}
