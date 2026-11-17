package com.uncross.core.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.uncross.core.book.LevelDetail;
import com.uncross.core.book.RestingOrder;
import com.uncross.core.events.EngineEvent;
import com.uncross.core.events.EventBus;
import com.uncross.core.events.OrderCancelled;
import com.uncross.core.events.OrderReduced;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderStatus;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Changing an order that is still waiting: what keeps its place in line, and what does not. */
class AmendTest {

    private static final String SYMBOL = "INFY";
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T09:15:00Z"), ZoneOffset.UTC);
    private final AtomicLong ids = new AtomicLong();
    private final EventBus bus = new EventBus();
    private final List<EngineEvent> events = new ArrayList<>();
    private final SymbolEngine engine;

    AmendTest() {
        bus.register(events::add);
        engine = new SymbolEngine(SYMBOL, new OrderFactory(ids::incrementAndGet, ids::incrementAndGet, clock),
                MatchingStrategies.standard(ids::incrementAndGet, clock), bus, clock);
    }

    private long bid(String trader, long price, long qty) {
        return engine.submit(new OrderRequest(SYMBOL, trader, Side.BUY, OrderType.LIMIT, price, qty)).incoming().id();
    }

    private List<String> queueAt(long price) {
        return engine.book().detail(Side.BUY, 10).stream().filter(l -> l.price() == price)
                .flatMap(l -> l.orders().stream()).map(RestingOrder::traderId).toList();
    }

    @Test
    void makingAnOrderSmallerKeepsItsPlaceInTheQueue() {
        long first = bid("FIRST", 15_000, 100);
        bid("SECOND", 15_000, 100);

        AmendResult result = engine.amend(first, 15_000, 40);

        assertThat(result.amended()).isTrue();
        assertThat(result.keptPriority()).isTrue();
        assertThat(result.order().id()).isEqualTo(first);
        assertThat(queueAt(15_000)).containsExactly("FIRST", "SECOND");
        assertThat(engine.snapshot(1).bids().get(0).totalQty()).isEqualTo(140);
        assertThat(events).last().isInstanceOfSatisfying(OrderReduced.class,
                e -> assertThat(e.previousRemaining()).isEqualTo(100));
    }

    @Test
    void changingThePriceIsACancelAndReplaceAtTheBackOfTheQueue() {
        long first = bid("FIRST", 15_000, 100);
        bid("SECOND", 15_100, 100);

        AmendResult result = engine.amend(first, 15_100, 100);

        assertThat(result.keptPriority()).isFalse();
        assertThat(result.order().id()).isNotEqualTo(first);
        assertThat(queueAt(15_100)).containsExactly("SECOND", "FIRST");
        assertThat(events).filteredOn(OrderCancelled.class::isInstance)
                .singleElement().extracting(e -> ((OrderCancelled) e).reason())
                .isEqualTo(SymbolEngine.REPLACED_BY_AMEND);
    }

    @Test
    void askingForMoreSharesAlsoLosesThePlace() {
        long first = bid("FIRST", 15_000, 100);
        bid("SECOND", 15_000, 100);

        engine.amend(first, 15_000, 300);

        assertThat(queueAt(15_000)).containsExactly("SECOND", "FIRST");
    }

    @Test
    void anAmendThatCrossesTheBookTradesAtOnce() {
        engine.submit(new OrderRequest(SYMBOL, "ASK", Side.SELL, OrderType.LIMIT, 15_200, 50));
        long mine = bid("ME", 15_000, 50);

        AmendResult result = engine.amend(mine, 15_200, 50);

        assertThat(result.replacementResult()).get().satisfies(r -> assertThat(r.filledQty()).isEqualTo(50));
    }

    @Test
    void aFinishedOrUnknownOrderCannotBeAmended() {
        assertThat(engine.amend(999, 15_000, 10).reason()).isEqualTo(CancelResult.UNKNOWN);

        long id = bid("ME", 15_000, 10);
        engine.cancel(id);
        assertThat(engine.amend(id, 15_000, 5).amended()).isFalse();
    }

    @Test
    void aWaitingStopCanBeMadeSmaller() {
        long stop = engine.submit(new OrderRequest(SYMBOL, "ME", Side.SELL, OrderType.STOP_LOSS, 14_000, 100))
                .incoming().id();

        AmendResult result = engine.amend(stop, 14_000, 60);

        assertThat(result.keptPriority()).isTrue();
        assertThat(engine.waitingStops()).singleElement()
                .satisfies(o -> assertThat(o.remainingQty()).isEqualTo(60));
        assertThat(result.order().status()).isEqualTo(OrderStatus.NEW);
    }
}
