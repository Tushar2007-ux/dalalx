package com.dalalx.core.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.book.LevelSnapshot;
import com.dalalx.core.book.OrderBook;
import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderFixtures;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Randomized ("property-based") test: submit 10,000 random LIMIT orders, with
 * some random cancels, and check that invariants which must <em>always</em> hold
 * really do. A fixed seed makes every run reproducible; if it fails, the seed in
 * the test name lets you replay exactly the same sequence.
 *
 * <p>Prices are clustered within ±20 ticks of ₹100 so that most orders cross
 * and lots of trading happens.
 */
class LimitOrderMatcherRandomizedTest {

    private static final int ORDER_COUNT = 10_000;
    private static final long MID = 10_000;
    private static final long TICK = 5;

    private final OrderFixtures orders = new OrderFixtures();
    private final OrderBook book = new OrderBook(OrderFixtures.SYMBOL);
    private final MatchingStrategy matcher = new LimitOrderMatcher(
            new AtomicLong()::incrementAndGet, Clock.fixed(OrderFixtures.T0, ZoneOffset.UTC));

    private final List<Order> allOrders = new ArrayList<>();
    private final Map<Long, Order> byId = new HashMap<>();
    private final Map<Long, Long> tradedQtyByOrder = new HashMap<>();
    private long tradeCount;

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 42, 2026})
    void invariantsHoldAcrossRandomOrderFlow(long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < ORDER_COUNT; i++) {
            if (random.nextInt(10) == 0 && !allOrders.isEmpty()) {
                cancelRandomOrder(random);
            }
            submitRandomOrder(random);
            assertBookNotCrossed();
        }

        assertThat(tradeCount).as("the test should actually generate trades").isGreaterThan(1_000);
        allOrders.forEach(this::assertQuantityConserved);
        assertBookMatchesActiveOrders();
    }

    private void submitRandomOrder(Random random) {
        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        long price = MID + TICK * (random.nextInt(41) - 20);
        long qty = 1 + random.nextInt(200);
        Order order = orders.limit(side, price, qty);
        allOrders.add(order);
        byId.put(order.id(), order);

        MatchResult result = matcher.match(order, book);

        result.trades().forEach(this::recordAndCheckTrade);
        assertThat(result.rested()).isEqualTo(order.isActive());
    }

    private void cancelRandomOrder(Random random) {
        Order victim = allOrders.get(random.nextInt(allOrders.size()));
        book.remove(victim.id()).ifPresent(Order::cancel); // only resting orders can be cancelled
    }

    private void recordAndCheckTrade(Trade trade) {
        tradeCount++;
        tradedQtyByOrder.merge(trade.buyOrderId(), trade.qty(), Long::sum);
        tradedQtyByOrder.merge(trade.sellOrderId(), trade.qty(), Long::sum);
        Order buy = byId.get(trade.buyOrderId());
        Order sell = byId.get(trade.sellOrderId());
        // No one trades worse than their limit.
        assertThat(trade.price()).isBetween(sell.limitPrice(), buy.limitPrice());
        // The price is the RESTING order's limit (the non-aggressor).
        Order resting = trade.aggressorSide() == Side.BUY ? sell : buy;
        assertThat(trade.price()).isEqualTo(resting.limitPrice());
    }

    private void assertBookNotCrossed() {
        if (book.bestBid().isPresent() && book.bestAsk().isPresent()) {
            assertThat(book.bestBid().getAsLong()).isLessThan(book.bestAsk().getAsLong());
        }
    }

    private void assertQuantityConserved(Order order) {
        assertThat(order.filledQty() + order.remainingQty()).isEqualTo(order.originalQty());
        assertThat(order.filledQty()).isEqualTo(tradedQtyByOrder.getOrDefault(order.id(), 0L));
        if (order.remainingQty() == 0) {
            assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        } else if (order.isActive()) {
            assertThat(book.find(order.id())).as("active order %d must rest", order.id()).containsSame(order);
        } else {
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    /** The book's per-level totals must agree with the orders actually resting. */
    private void assertBookMatchesActiveOrders() {
        List<Order> active = allOrders.stream().filter(Order::isActive).toList();
        BookSnapshot snap = book.snapshot(Integer.MAX_VALUE);
        long bookQty = sumQty(snap.bids()) + sumQty(snap.asks());
        long bookCount = sumCount(snap.bids()) + sumCount(snap.asks());

        assertThat(book.orderCount()).isEqualTo(active.size());
        assertThat(bookCount).isEqualTo(active.size());
        assertThat(bookQty).isEqualTo(active.stream().mapToLong(Order::remainingQty).sum());
    }

    private static long sumQty(List<LevelSnapshot> levels) {
        return levels.stream().mapToLong(LevelSnapshot::totalQty).sum();
    }

    private static long sumCount(List<LevelSnapshot> levels) {
        return levels.stream().mapToLong(LevelSnapshot::orderCount).sum();
    }
}
