package com.uncross.core.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.uncross.core.book.LevelSnapshot;
import com.uncross.core.events.EngineEvent;
import com.uncross.core.events.EventBus;
import com.uncross.core.events.OrderCancelled;
import com.uncross.core.events.TradeExecuted;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderStatus;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import com.uncross.core.model.Trade;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** The two order types added after the first three: stop-loss and iceberg. */
class StopLossAndIcebergTest {

    private static final String SYMBOL = "TCS";
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T09:15:00Z"), ZoneOffset.UTC);
    private final AtomicLong ids = new AtomicLong();
    private final EventBus bus = new EventBus();
    private final List<EngineEvent> events = new ArrayList<>();
    private final SymbolEngine engine;

    StopLossAndIcebergTest() {
        bus.register(events::add);
        engine = new SymbolEngine(SYMBOL, new OrderFactory(ids::incrementAndGet, ids::incrementAndGet, clock),
                MatchingStrategies.standard(ids::incrementAndGet, clock), bus, clock);
    }

    private MatchResult send(String trader, Side side, OrderType type, long price, long qty) {
        return engine.submit(new OrderRequest(SYMBOL, trader, side, type, price, qty));
    }

    private MatchResult iceberg(String trader, Side side, long price, long qty, long display) {
        return engine.submit(new OrderRequest(SYMBOL, trader, side, OrderType.ICEBERG, price, qty, display));
    }

    private List<Trade> trades() {
        return events.stream().filter(TradeExecuted.class::isInstance).map(e -> ((TradeExecuted) e).trade()).toList();
    }

    // ---- stop-loss ----------------------------------------------------------------

    @Test
    void aStopWaitsOutOfSightUntilThePriceReachesItsTrigger() {
        send("BID", Side.BUY, OrderType.LIMIT, 9_600, 100);
        send("BID", Side.BUY, OrderType.LIMIT, 9_400, 300);
        MatchResult stop = send("ME", Side.SELL, OrderType.STOP_LOSS, 9_500, 50);

        assertThat(stop.status()).isEqualTo(OrderStatus.NEW);
        assertThat(engine.snapshot(5).asks()).isEmpty(); // nobody can see or hit a stop
        assertThat(engine.waitingStops()).hasSize(1);

        send("SELLER", Side.SELL, OrderType.MARKET, 0, 100); // trades at 9,600: above the trigger
        assertThat(engine.waitingStops()).hasSize(1);

        send("SELLER", Side.SELL, OrderType.MARKET, 0, 10);  // trades at 9,400: the stop fires
        assertThat(engine.waitingStops()).isEmpty();
        assertThat(trades()).last().satisfies(t -> {
            assertThat(t.sellerId()).isEqualTo("ME");
            assertThat(t.price()).isEqualTo(9_400);       // sold at the market, below the trigger
            assertThat(t.qty()).isEqualTo(50);
        });
        assertThat(stop.incoming().status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void aBuyStopFiresWhenThePriceRises() {
        send("ASK", Side.SELL, OrderType.LIMIT, 10_100, 100);
        send("ASK", Side.SELL, OrderType.LIMIT, 10_300, 100);
        send("ME", Side.BUY, OrderType.STOP_LOSS, 10_100, 40);

        send("BUYER", Side.BUY, OrderType.MARKET, 0, 10); // trades at 10,100 = the trigger

        assertThat(trades()).extracting(Trade::buyerId).containsExactly("BUYER", "ME");
    }

    @Test
    void aStopPlacedPastTheLastPriceFiresAtOnce() {
        send("A", Side.BUY, OrderType.LIMIT, 9_000, 100);
        send("B", Side.SELL, OrderType.MARKET, 0, 1); // last price is now 9,000

        MatchResult stop = send("ME", Side.SELL, OrderType.STOP_LOSS, 9_500, 20);

        assertThat(stop.incoming().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(engine.waitingStops()).isEmpty();
    }

    @Test
    void oneStopsSaleCanSetOffTheNext() {
        send("BID", Side.BUY, OrderType.LIMIT, 9_900, 10);
        send("BID", Side.BUY, OrderType.LIMIT, 9_700, 10);
        send("BID", Side.BUY, OrderType.LIMIT, 9_500, 10);
        send("FIRST", Side.SELL, OrderType.STOP_LOSS, 9_900, 15); // will sell through 9,900 into 9,700
        send("SECOND", Side.SELL, OrderType.STOP_LOSS, 9_700, 5);

        send("X", Side.SELL, OrderType.MARKET, 0, 5); // trades at 9,900 and starts the chain

        assertThat(trades()).extracting(Trade::sellerId, Trade::price).containsExactly(
                org.assertj.core.groups.Tuple.tuple("X", 9_900L),
                org.assertj.core.groups.Tuple.tuple("FIRST", 9_900L),
                org.assertj.core.groups.Tuple.tuple("FIRST", 9_700L),
                org.assertj.core.groups.Tuple.tuple("SECOND", 9_500L)); // 9,700 was used up by FIRST
    }

    @Test
    void aWaitingStopCanBeCancelled() {
        long id = send("ME", Side.SELL, OrderType.STOP_LOSS, 9_500, 50).incoming().id();

        CancelResult result = engine.cancel(id);

        assertThat(result.cancelled()).isTrue();
        assertThat(engine.waitingStops()).isEmpty();
        assertThat(events).last().isInstanceOf(OrderCancelled.class);
    }

    @Test
    void aTriggeredStopWithNothingToHitIsCancelledNotRejected() {
        send("A", Side.BUY, OrderType.LIMIT, 9_000, 1);
        send("B", Side.SELL, OrderType.MARKET, 0, 1); // last 9,000; the book is now empty

        MatchResult stop = send("ME", Side.SELL, OrderType.STOP_LOSS, 9_500, 20);

        assertThat(stop.incoming().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ---- iceberg ------------------------------------------------------------------

    @Test
    void anIcebergShowsOnlyOneSlice() {
        iceberg("FUND", Side.SELL, 10_000, 1_000, 100);

        assertThat(engine.snapshot(5).asks()).extracting(LevelSnapshot::totalQty).containsExactly(100L);
    }

    @Test
    void theNextSliceJoinsTheBackOfTheQueue() {
        iceberg("FUND", Side.SELL, 10_000, 300, 100);
        send("SMALL", Side.SELL, OrderType.LIMIT, 10_000, 50); // arrives after the iceberg's first slice

        send("BUYER", Side.BUY, OrderType.LIMIT, 10_000, 150);

        // The first slice (100) fills, then the next slice goes behind SMALL, so SMALL is next.
        assertThat(trades()).extracting(Trade::sellerId, Trade::qty).containsExactly(
                org.assertj.core.groups.Tuple.tuple("FUND", 100L),
                org.assertj.core.groups.Tuple.tuple("SMALL", 50L));
        assertThat(engine.snapshot(5).asks()).extracting(LevelSnapshot::totalQty).containsExactly(100L);
    }

    @Test
    void anIcebergIsFilledSliceBySliceAgainstABigOrder() {
        iceberg("FUND", Side.SELL, 10_000, 250, 100);

        MatchResult buy = send("BUYER", Side.BUY, OrderType.MARKET, 0, 250);

        assertThat(buy.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(trades()).extracting(Trade::qty).containsExactly(100L, 100L, 50L);
        assertThat(engine.snapshot(5).asks()).isEmpty();
    }

    @Test
    void anArrivingIcebergTakesWithItsWholeSize() {
        send("ASK", Side.SELL, OrderType.LIMIT, 10_000, 400);

        MatchResult ice = iceberg("FUND", Side.BUY, 10_000, 1_000, 100);

        assertThat(ice.filledQty()).isEqualTo(400);
        assertThat(engine.snapshot(5).bids()).extracting(LevelSnapshot::totalQty).containsExactly(100L);
    }

    @Test
    void anIcebergMustShowLessThanItsSize() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OrderRequest(SYMBOL, "F", Side.BUY, OrderType.ICEBERG, 10_000, 100, 100));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OrderRequest(SYMBOL, "F", Side.BUY, OrderType.LIMIT, 10_000, 100, 10));
    }
}
