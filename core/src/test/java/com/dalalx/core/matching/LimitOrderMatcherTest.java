package com.dalalx.core.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.dalalx.core.book.LevelSnapshot;
import com.dalalx.core.book.OrderBook;
import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderFixtures;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LimitOrderMatcherTest {

    private final OrderFixtures orders = new OrderFixtures();
    private final OrderBook book = new OrderBook(OrderFixtures.SYMBOL);
    private final Clock clock = Clock.fixed(OrderFixtures.T0, ZoneOffset.UTC);
    private final MatchingStrategy matcher = new LimitOrderMatcher(new AtomicLong()::incrementAndGet, clock);

    /** Puts an order straight into the book as a resting order. */
    private Order rest(Side side, long price, long qty) {
        Order order = orders.limit(side, price, qty);
        book.add(order);
        return order;
    }

    private MatchResult submit(Side side, long price, long qty) {
        return matcher.match(orders.limit(side, price, qty), book);
    }

    @Test
    void orderThatDoesNotCrossRests() {
        rest(Side.SELL, 10100, 10);

        MatchResult result = submit(Side.BUY, 10000, 10);

        assertThat(result.trades()).isEmpty();
        assertThat(result.rested()).isTrue();
        assertThat(result.incoming().status()).isEqualTo(OrderStatus.NEW);
        assertThat(book.bestBid()).hasValue(10000);
        assertThat(book.bestAsk()).hasValue(10100);
    }

    @Test
    void orderIntoEmptyBookRests() {
        MatchResult result = submit(Side.SELL, 10000, 10);

        assertThat(result.trades()).isEmpty();
        assertThat(result.rested()).isTrue();
        assertThat(book.bestAsk()).hasValue(10000);
    }

    @Test
    void exactMatchFillsBothAndEmptiesBook() {
        Order resting = rest(Side.SELL, 10000, 10);

        MatchResult result = submit(Side.BUY, 10000, 10);

        assertThat(result.trades()).singleElement().satisfies(t -> {
            assertThat(t.price()).isEqualTo(10000);
            assertThat(t.qty()).isEqualTo(10);
        });
        assertThat(result.incoming().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(resting.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.rested()).isFalse();
        assertThat(book.isEmpty()).isTrue();
    }

    @Test
    void incomingEatsSeveralLevelsBestPriceFirst() {
        rest(Side.SELL, 10000, 5);
        rest(Side.SELL, 10050, 5);
        rest(Side.SELL, 10100, 5);
        rest(Side.SELL, 10200, 5);

        MatchResult result = submit(Side.BUY, 10100, 12);

        assertThat(result.trades()).extracting(Trade::price, Trade::qty).containsExactly(
                tuple(10000L, 5L),
                tuple(10050L, 5L),
                tuple(10100L, 2L));
        assertThat(result.incoming().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.filledQty()).isEqualTo(12);
        assertThat(book.snapshot(10).asks())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("mainTrader") // trader names are new in Step 11
                .containsExactly(
                new LevelSnapshot(10100, 3, 1),
                new LevelSnapshot(10200, 5, 1));
    }

    @Test
    void remainderAfterSweepingRestsAtLimitPrice() {
        rest(Side.SELL, 10000, 5);
        rest(Side.SELL, 10050, 5);
        rest(Side.SELL, 10100, 5);

        MatchResult result = submit(Side.BUY, 10050, 15);

        assertThat(result.filledQty()).isEqualTo(10);
        assertThat(result.rested()).isTrue();
        assertThat(result.incoming().status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(result.incoming().remainingQty()).isEqualTo(5);
        assertThat(book.snapshot(10).bids())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("mainTrader") // trader names are new in Step 11
                .containsExactly(new LevelSnapshot(10050, 5, 1));
        assertThat(book.bestAsk()).hasValue(10100);
    }

    @Test
    void smallIncomingPartiallyFillsRestingOrderWhichStaysInBook() {
        Order resting = rest(Side.SELL, 10000, 10);

        MatchResult result = submit(Side.BUY, 10000, 4);

        assertThat(result.incoming().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(resting.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(resting.remainingQty()).isEqualTo(6);
        assertThat(book.find(resting.id())).containsSame(resting);
    }

    @Test
    void partiallyFilledRestingOrderKeepsItsQueuePosition() {
        Order first = rest(Side.SELL, 10000, 10);
        Order second = rest(Side.SELL, 10000, 10);

        submit(Side.BUY, 10000, 4);          // first: 6 left, still at the front
        MatchResult next = submit(Side.BUY, 10000, 8);

        assertThat(next.trades()).extracting(Trade::sellOrderId, Trade::qty).containsExactly(
                tuple(first.id(), 6L),
                tuple(second.id(), 2L));
        assertThat(first.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(second.remainingQty()).isEqualTo(8);
    }

    @Test
    void samePriceOrdersFillInArrivalOrder() {
        Order a = rest(Side.BUY, 10000, 5);
        Order b = rest(Side.BUY, 10000, 5);
        Order c = rest(Side.BUY, 10000, 5);

        MatchResult result = submit(Side.SELL, 10000, 12);

        assertThat(result.trades()).extracting(Trade::buyOrderId)
                .containsExactly(a.id(), b.id(), c.id());
        assertThat(c.remainingQty()).isEqualTo(3);
    }

    @Test
    void betterPriceBeatsEarlierArrival() {
        Order early = rest(Side.SELL, 10100, 5);
        Order betterPrice = rest(Side.SELL, 10000, 5);

        MatchResult result = submit(Side.BUY, 10100, 5);

        assertThat(result.trades()).extracting(Trade::sellOrderId).containsExactly(betterPrice.id());
        assertThat(early.status()).isEqualTo(OrderStatus.NEW);
    }

    @Test
    void buyerPaysRestingAskNotItsOwnHigherLimit() {
        rest(Side.SELL, 10000, 5);

        MatchResult result = submit(Side.BUY, 10500, 5);

        assertThat(result.trades()).singleElement().extracting(Trade::price).isEqualTo(10000L);
    }

    @Test
    void sellerGetsRestingBidNotItsOwnLowerLimit() {
        rest(Side.BUY, 10500, 5);

        MatchResult result = submit(Side.SELL, 10000, 5);

        assertThat(result.trades()).singleElement().extracting(Trade::price).isEqualTo(10500L);
    }

    @Test
    void tradeRecordsBuyerSellerAggressorIdAndTime() {
        Order restingBuy = rest(Side.BUY, 10000, 5);
        Order incomingSell = orders.limit("SELLER", Side.SELL, 10000, 5);

        Trade trade = matcher.match(incomingSell, book).trades().getFirst();

        assertThat(trade.id()).isEqualTo(1);
        assertThat(trade.symbol()).isEqualTo("TCS");
        assertThat(trade.buyOrderId()).isEqualTo(restingBuy.id());
        assertThat(trade.sellOrderId()).isEqualTo(incomingSell.id());
        assertThat(trade.buyerId()).isEqualTo(restingBuy.traderId());
        assertThat(trade.sellerId()).isEqualTo("SELLER");
        assertThat(trade.aggressorSide()).isEqualTo(Side.SELL);
        assertThat(trade.timestamp()).isEqualTo(OrderFixtures.T0);
    }

    @Test
    void tradeIdsComeFromSupplierInOrder() {
        rest(Side.SELL, 10000, 1);
        rest(Side.SELL, 10000, 1);
        rest(Side.SELL, 10000, 1);

        MatchResult result = submit(Side.BUY, 10000, 3);

        assertThat(result.trades()).extracting(Trade::id).containsExactly(1L, 2L, 3L);
    }

    @Test
    void resultTradeListIsImmutable() {
        rest(Side.SELL, 10000, 1);
        MatchResult result = submit(Side.BUY, 10000, 1);
        assertThatThrownBy(() -> result.trades().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNonLimitOrders() {
        Order ioc = orders.of(OrderType.IOC, Side.BUY, 10000, 1);
        Order market = orders.of(OrderType.MARKET, Side.BUY, 0, 1);
        assertThatIllegalArgumentException().isThrownBy(() -> matcher.match(ioc, book));
        assertThatIllegalArgumentException().isThrownBy(() -> matcher.match(market, book));
    }

    @Test
    void rejectsInactiveIncomingOrder() {
        Order cancelled = orders.limit(Side.BUY, 10000, 1);
        cancelled.cancel();
        assertThatIllegalArgumentException().isThrownBy(() -> matcher.match(cancelled, book));
    }

    @Test
    void rejectsOrderForAnotherSymbol() {
        Order infy = new Order(99, 99, OrderFixtures.T0,
                new OrderRequest("INFY", "T1", Side.BUY, OrderType.LIMIT, 10000, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> matcher.match(infy, book));
    }
}
