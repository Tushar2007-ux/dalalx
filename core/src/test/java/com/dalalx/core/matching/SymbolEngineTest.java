package com.dalalx.core.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.book.LevelSnapshot;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SymbolEngineTest {

    private static final String SYMBOL = "RELIANCE";
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T09:15:00Z"), ZoneOffset.UTC);
    private final AtomicLong orderCounter = new AtomicLong();
    private final SymbolEngine engine = new SymbolEngine(SYMBOL,
            new OrderFactory(orderCounter::incrementAndGet, orderCounter::get, clock),
            MatchingStrategies.standard(new AtomicLong()::incrementAndGet, clock));

    private MatchResult limit(Side side, long price, long qty) {
        return engine.submit(new OrderRequest(SYMBOL, "T", side, OrderType.LIMIT, price, qty));
    }

    private MatchResult order(OrderType type, Side side, long price, long qty) {
        return engine.submit(new OrderRequest(SYMBOL, "T", side, type, price, qty));
    }

    @Test
    void routesEachOrderTypeToItsOwnStrategy() {
        limit(Side.SELL, 248650, 50);

        MatchResult market = order(OrderType.MARKET, Side.BUY, 0, 20);
        MatchResult ioc = order(OrderType.IOC, Side.BUY, 248000, 10);   // too low: cancelled
        MatchResult rest = limit(Side.BUY, 248000, 10);                  // too low: rests

        assertThat(market.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(ioc.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(rest.rested()).isTrue();
    }

    @Test
    void refusesAnOrderForAnotherSymbol() {
        OrderRequest tcs = new OrderRequest("TCS", "T", Side.BUY, OrderType.LIMIT, 410000, 1);

        assertThatIllegalArgumentException().isThrownBy(() -> engine.submit(tcs)).withMessageContaining("TCS");
    }

    @Test
    void cancellingARestingOrderRemovesItFromTheBook() {
        long id = limit(Side.BUY, 248000, 10).incoming().id();

        CancelResult result = engine.cancel(id);

        assertThat(result.cancelled()).isTrue();
        assertThat(result.reason()).isNull();
        assertThat(result.knownOrder()).get().extracting(o -> o.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(engine.snapshot(5).bids()).isEmpty();
    }

    @Test
    void cancellingAPartlyFilledOrderCancelsOnlyTheRest() {
        long id = limit(Side.SELL, 248650, 100).incoming().id();
        limit(Side.BUY, 248650, 30);

        CancelResult result = engine.cancel(id);

        assertThat(result.cancelled()).isTrue();
        assertThat(result.order().filledQty()).isEqualTo(30);
        assertThat(result.order().remainingQty()).isEqualTo(70);
        assertThat(engine.snapshot(5).asks()).isEmpty();
    }

    @Test
    void cancellingAnUnknownOrderFailsWithAReason() {
        CancelResult result = engine.cancel(999);

        assertThat(result.cancelled()).isFalse();
        assertThat(result.reason()).isEqualTo(CancelResult.UNKNOWN);
        assertThat(result.knownOrder()).isEmpty();
    }

    @Test
    void cancellingAFilledOrderFailsBecauseItAlreadyTraded() {
        long restingId = limit(Side.SELL, 248650, 10).incoming().id();
        limit(Side.BUY, 248650, 10); // fills the resting order

        CancelResult result = engine.cancel(restingId);

        assertThat(result.cancelled()).isFalse();
        assertThat(result.reason()).contains("FILLED");
    }

    @Test
    void cancellingTwiceFailsTheSecondTime() {
        long id = limit(Side.BUY, 248000, 10).incoming().id();
        engine.cancel(id);

        CancelResult second = engine.cancel(id);

        assertThat(second.cancelled()).isFalse();
        assertThat(second.reason()).contains("CANCELLED");
    }

    @Test
    void cancellingAnIocThatWasAlreadyCancelledFails() {
        long id = order(OrderType.IOC, Side.BUY, 248000, 10).incoming().id();

        assertThat(engine.cancel(id).reason()).contains("CANCELLED");
    }

    @Test
    void cancellingARejectedMarketOrderFails() {
        long id = order(OrderType.MARKET, Side.SELL, 0, 10).incoming().id();

        assertThat(engine.cancel(id).reason()).contains("REJECTED");
    }

    @Test
    void historyIsBoundedSoMemoryCannotGrowForever() {
        SymbolEngine small = new SymbolEngine(SYMBOL,
                new OrderFactory(orderCounter::incrementAndGet, orderCounter::get, clock),
                MatchingStrategies.standard(new AtomicLong()::incrementAndGet, clock), 2);
        long first = small.submit(new OrderRequest(SYMBOL, "T", Side.BUY, OrderType.IOC, 100, 1)).incoming().id();
        small.submit(new OrderRequest(SYMBOL, "T", Side.BUY, OrderType.IOC, 100, 1));
        small.submit(new OrderRequest(SYMBOL, "T", Side.BUY, OrderType.IOC, 100, 1));

        assertThat(small.cancel(first).reason()).isEqualTo(CancelResult.UNKNOWN); // forgotten
    }

    @Test
    void snapshotShowsTheBookBestFirst() {
        limit(Side.BUY, 248000, 10);
        limit(Side.BUY, 248050, 5);
        limit(Side.SELL, 248650, 7);

        BookSnapshot snapshot = engine.snapshot(10);

        assertThat(snapshot.symbol()).isEqualTo(SYMBOL);
        assertThat(snapshot.bids()).extracting(LevelSnapshot::price, LevelSnapshot::totalQty)
                .containsExactly(tuple(248050L, 5L), tuple(248000L, 10L));
        assertThat(snapshot.asks()).extracting(LevelSnapshot::price).containsExactly(248650L);
    }
}
