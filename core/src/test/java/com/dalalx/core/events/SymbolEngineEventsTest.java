package com.dalalx.core.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.matching.MatchingStrategies;
import com.dalalx.core.matching.OrderFactory;
import com.dalalx.core.matching.SymbolEngine;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Checks that {@link SymbolEngine} announces everything, in the documented order. */
class SymbolEngineEventsTest {

    private static final String SYMBOL = "INFY";
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T09:15:00Z"), ZoneOffset.UTC);
    private final EventBus bus = new EventBus();
    private final RecordingListener recorder = new RecordingListener();
    private final AtomicLong ids = new AtomicLong();
    private final SymbolEngine engine = new SymbolEngine(SYMBOL,
            new OrderFactory(ids::incrementAndGet, ids::get, clock),
            MatchingStrategies.standard(new AtomicLong()::incrementAndGet, clock), bus, clock);

    @BeforeEach
    void listen() {
        bus.register(recorder);
    }

    private long submit(OrderType type, Side side, long price, long qty) {
        return engine.submit(new OrderRequest(SYMBOL, "T", side, type, price, qty)).incoming().id();
    }

    @Test
    void restingOrderIsAcceptedThenTheBookChanges() {
        submit(OrderType.LIMIT, Side.BUY, 153000, 10);

        assertThat(recorder.types()).containsExactly("OrderAccepted", "BookChanged");
        BookChanged book = recorder.eventsOf(BookChanged.class).getFirst();
        assertThat(book.book().bids()).singleElement().satisfies(l -> assertThat(l.price()).isEqualTo(153000));
    }

    @Test
    void multiFillOrderPublishesAcceptedThenEachTradeThenOneBookChange() {
        submit(OrderType.LIMIT, Side.SELL, 153000, 10);
        submit(OrderType.LIMIT, Side.SELL, 153050, 10);
        submit(OrderType.LIMIT, Side.SELL, 153100, 10);
        recorder.clear();

        submit(OrderType.LIMIT, Side.BUY, 153100, 25);

        assertThat(recorder.types()).containsExactly(
                "OrderAccepted", "TradeExecuted", "TradeExecuted", "TradeExecuted", "BookChanged");
        assertThat(recorder.eventsOf(TradeExecuted.class)).extracting(e -> e.trade().price())
                .containsExactly(153000L, 153050L, 153100L);
    }

    @Test
    void acceptedEventShowsTheOrderAsItArrivedNotAfterFilling() {
        submit(OrderType.LIMIT, Side.SELL, 153000, 10);
        recorder.clear();

        submit(OrderType.LIMIT, Side.BUY, 153000, 10);

        OrderAccepted accepted = recorder.eventsOf(OrderAccepted.class).getFirst();
        assertThat(accepted.order().status()).isEqualTo(OrderStatus.NEW);
        assertThat(accepted.order().remainingQty()).isEqualTo(10);
    }

    @Test
    void rejectedMarketOrderPublishesOnlyARejection() {
        submit(OrderType.MARKET, Side.BUY, 0, 10);

        assertThat(recorder.types()).containsExactly("OrderRejected");
        assertThat(recorder.eventsOf(OrderRejected.class).getFirst().reason()).contains("no resting orders");
    }

    @Test
    void iocRemainderIsCancelledAfterItsTrades() {
        submit(OrderType.LIMIT, Side.SELL, 153000, 5);
        recorder.clear();

        submit(OrderType.IOC, Side.BUY, 153000, 20);

        assertThat(recorder.types()).containsExactly("OrderAccepted", "TradeExecuted", "OrderCancelled", "BookChanged");
        OrderCancelled cancelled = recorder.eventsOf(OrderCancelled.class).getFirst();
        assertThat(cancelled.order().remainingQty()).isEqualTo(15);
    }

    @Test
    void iocThatTouchesNothingDoesNotAnnounceABookChange() {
        submit(OrderType.IOC, Side.BUY, 153000, 20);

        assertThat(recorder.types()).containsExactly("OrderAccepted", "OrderCancelled");
    }

    @Test
    void traderCancelPublishesCancelledThenBookChanged() {
        long id = submit(OrderType.LIMIT, Side.BUY, 153000, 10);
        recorder.clear();

        engine.cancel(id);

        assertThat(recorder.types()).containsExactly("OrderCancelled", "BookChanged");
        assertThat(recorder.eventsOf(BookChanged.class).getFirst().book().bids()).isEmpty();
    }

    @Test
    void failedCancelPublishesNothing() {
        engine.cancel(12345);

        assertThat(recorder.events()).isEmpty();
    }

    @Test
    void eventSequenceNumbersIncreaseInPublishOrder() {
        submit(OrderType.LIMIT, Side.SELL, 153000, 10);
        submit(OrderType.LIMIT, Side.BUY, 153000, 10);

        assertThat(recorder.events()).extracting(EngineEvent::sequence).isSorted().doesNotHaveDuplicates();
        assertThat(recorder.events()).allSatisfy(e -> assertThat(e.symbol()).isEqualTo(SYMBOL));
    }
}
