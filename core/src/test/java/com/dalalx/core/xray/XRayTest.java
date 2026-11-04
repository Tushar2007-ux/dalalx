package com.dalalx.core.xray;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.dalalx.core.book.LevelSnapshot;
import com.dalalx.core.book.RestingOrder;
import com.dalalx.core.breaker.BreakerRule;
import com.dalalx.core.breaker.TradingSession;
import com.dalalx.core.events.EventBus;
import com.dalalx.core.matching.MatchingStrategies;
import com.dalalx.core.matching.OrderFactory;
import com.dalalx.core.matching.SymbolEngine;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import com.dalalx.core.xray.XRay.Step;
import com.dalalx.core.xray.XRay.StepKind;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** The worked example from the X-Ray design: buy 200 RELIANCE at no more than ₹2,487.50. */
class XRayTest {

    private static final Map<String, String> NAMES = Map.of("MUNIM", "The Munim", "MOMENTUM", "Momentum Chaser",
            "PANIC", "Panic Seller", "YOU", "You");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T09:15:00Z"), ZoneOffset.UTC);
    private final AtomicLong ids = new AtomicLong();
    private final XRayDesk desk = new XRayDesk(Set.of("YOU"), id -> NAMES.getOrDefault(id, id));
    private final SymbolEngine engine = new SymbolEngine("RELIANCE",
            new OrderFactory(ids::incrementAndGet, ids::incrementAndGet, clock),
            MatchingStrategies.standard(ids::incrementAndGet, clock), new EventBus(), clock,
            TradingSession.withBreaker("RELIANCE", BreakerRule.STANDARD));

    XRayTest() {
        engine.recordXRays(desk);
    }

    private void ask(String trader, long price, long qty) {
        engine.submit(new OrderRequest("RELIANCE", trader, Side.SELL, OrderType.LIMIT, price, qty));
    }

    private void setTheBook() {
        ask("MUNIM", 248675, 50);
        ask("MOMENTUM", 248700, 80);
        ask("PANIC", 248725, 30);
        ask("MUNIM", 248775, 400);
    }

    private XRay buy(OrderType type, long price, long qty) {
        long id = engine.submit(new OrderRequest("RELIANCE", "YOU", Side.BUY, type, price, qty)).incoming().id();
        return desk.find(id).orElseThrow();
    }

    @Test
    void recordsTheBookAsItWasBeforeTheOrderArrived() {
        setTheBook();
        XRay xray = buy(OrderType.LIMIT, 248750, 200);

        assertThat(xray.bookBefore()).hasSize(4);
        assertThat(xray.bookBefore().get(0).orders()).extracting(RestingOrder::traderId, RestingOrder::remainingQty)
                .containsExactly(tuple("MUNIM", 50L));
        assertThat(xray.fills()).extracting(XRay.Fill::price, XRay.Fill::qty, XRay.Fill::counterparty, XRay.Fill::level)
                .containsExactly(tuple(248675L, 50L, "MUNIM", 0), tuple(248700L, 80L, "MOMENTUM", 1),
                        tuple(248725L, 30L, "PANIC", 2));
        assertThat(xray.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(xray.remaining()).isEqualTo(40);
    }

    @Test
    void narratesTheMatchStepByStep() {
        setTheBook();
        XRay xray = buy(OrderType.LIMIT, 248750, 200);

        assertThat(xray.steps()).extracting(Step::kind).containsExactly(StepKind.ARRIVE, StepKind.CHECK,
                StepKind.FILL, StepKind.FILL, StepKind.FILL, StepKind.STOP, StepKind.OUTCOME);
        assertThat(xray.steps()).extracting(Step::body).containsExactly(
                "A limit order to buy 200 RELIANCE at no more than ₹2,487.50 a share. It goes to the sell side of the book first.",
                "The best seller is at ₹2,486.75. That is within your limit, so the order can trade straight away.",
                "The Munim's order at ₹2,486.75 is first in line: 50 shares trade at its price. 150 still to buy.",
                "Momentum Chaser's order at ₹2,487.00 is next: 80 shares trade at its price. 70 still to buy.",
                "Panic Seller's order at ₹2,487.25 is next: 30 shares trade at its price. 40 still to buy.",
                "The next price is ₹2,487.75, above your limit of ₹2,487.50. Matching stops here.",
                "The remaining 40 shares rest as a bid at ₹2,487.50. They keep their place in the queue until someone trades with them or you cancel.");
        assertThat(xray.steps().get(2).title()).isEqualTo("① 50 shares at ₹2,486.75");
    }

    @Test
    void anIocThatRunsOutOfBookIsCancelled() {
        ask("MUNIM", 248675, 50);
        XRay xray = buy(OrderType.IOC, 249000, 80);

        assertThat(xray.steps()).extracting(Step::title).containsExactly("The order arrives", "Check the best ask",
                "① 50 shares at ₹2,486.75", "The book runs out", "The rest is cancelled");
    }

    @Test
    void aMarketOrderOnAnEmptyBookIsRejected() {
        XRay xray = buy(OrderType.MARKET, 0, 10);

        assertThat(xray.steps()).extracting(Step::kind).containsExactly(StepKind.ARRIVE, StepKind.CHECK, StepKind.STOP, StepKind.OUTCOME);
        assertThat(xray.steps().get(1).body()).isEqualTo("There are no sellers in the book at all, so nothing can trade.");
        assertThat(xray.status()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void aLimitThatDoesNotReachTheBestAskJustRests() {
        setTheBook();
        XRay xray = buy(OrderType.LIMIT, 248000, 10);

        assertThat(xray.steps()).extracting(Step::kind).containsExactly(StepKind.ARRIVE, StepKind.CHECK, StepKind.OUTCOME);
        assertThat(xray.steps().get(1).body()).endsWith("beyond your limit of ₹2,480.00, so nothing trades.");
    }

    @Test
    void ordersFromBotsAreNotXRayed() {
        setTheBook();
        assertThat(desk.recent("MUNIM", 10)).isEmpty();
        buy(OrderType.LIMIT, 248000, 10);
        assertThat(desk.latest("YOU")).isPresent();
    }

    @Test
    void bookLevelsNameTheTraderWithTheMostShares() {
        ask("MUNIM", 248675, 50);
        ask("WHALE", 248675, 300);
        ask("PANIC", 248675, 20);

        LevelSnapshot level = engine.snapshot(1).asks().get(0);
        assertThat(level.mainTrader()).isEqualTo("WHALE");
        assertThat(level.orderCount()).isEqualTo(3);
    }
}
