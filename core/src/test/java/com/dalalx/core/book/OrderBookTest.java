package com.dalalx.core.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderFixtures;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderBookTest {

    private final OrderFixtures orders = new OrderFixtures();
    private final OrderBook book = new OrderBook(OrderFixtures.SYMBOL);

    @Test
    void emptyBookHasNoBestPricesOrSpread() {
        assertThat(book.isEmpty()).isTrue();
        assertThat(book.bestBid()).isEmpty();
        assertThat(book.bestAsk()).isEmpty();
        assertThat(book.spread()).isEmpty();
        assertThat(book.peekBest(Side.BUY)).isEmpty();
    }

    @Nested
    class Ordering {

        @Test
        void bestBidIsHighestPrice() {
            book.add(orders.limit(Side.BUY, 9900, 1));
            book.add(orders.limit(Side.BUY, 9950, 1));
            book.add(orders.limit(Side.BUY, 9800, 1));

            assertThat(book.bestBid()).hasValue(9950);
        }

        @Test
        void bestAskIsLowestPrice() {
            book.add(orders.limit(Side.SELL, 10100, 1));
            book.add(orders.limit(Side.SELL, 10050, 1));
            book.add(orders.limit(Side.SELL, 10200, 1));

            assertThat(book.bestAsk()).hasValue(10050);
        }

        @Test
        void spreadIsAskMinusBid() {
            book.add(orders.limit(Side.BUY, 9950, 1));
            book.add(orders.limit(Side.SELL, 10050, 1));

            assertThat(book.spread()).hasValue(100);
        }

        @Test
        void spreadNeedsBothSides() {
            book.add(orders.limit(Side.BUY, 9950, 1));
            assertThat(book.spread()).isEmpty();
        }

        @Test
        void snapshotListsBidsHighFirstAndAsksLowFirst() {
            book.add(orders.limit(Side.BUY, 9800, 1));
            book.add(orders.limit(Side.BUY, 9900, 1));
            book.add(orders.limit(Side.SELL, 10200, 1));
            book.add(orders.limit(Side.SELL, 10100, 1));

            BookSnapshot snap = book.snapshot(10);

            assertThat(snap.bids()).extracting(LevelSnapshot::price).containsExactly(9900L, 9800L);
            assertThat(snap.asks()).extracting(LevelSnapshot::price).containsExactly(10100L, 10200L);
        }

        @Test
        void peekBestReturnsOldestOrderAtBestPrice() {
            Order first = orders.limit(Side.SELL, 10100, 5);
            book.add(first);
            book.add(orders.limit(Side.SELL, 10100, 5));
            book.add(orders.limit(Side.SELL, 10200, 5));

            assertThat(book.peekBest(Side.SELL)).containsSame(first);
        }
    }

    @Nested
    class Snapshot {

        @Test
        void aggregatesQuantityAndCountPerLevel() {
            book.add(orders.limit(Side.BUY, 9900, 10));
            book.add(orders.limit(Side.BUY, 9900, 15));
            book.add(orders.limit(Side.BUY, 9800, 7));
            book.add(orders.limit(Side.SELL, 10100, 4));

            BookSnapshot snap = book.snapshot(5);

            assertThat(snap.symbol()).isEqualTo("TCS");
            assertThat(snap.bids()).containsExactly(
                    new LevelSnapshot(9900, 25, 2),
                    new LevelSnapshot(9800, 7, 1));
            assertThat(snap.asks()).containsExactly(new LevelSnapshot(10100, 4, 1));
        }

        @Test
        void limitsToDepth() {
            for (int i = 0; i < 5; i++) {
                book.add(orders.limit(Side.BUY, 9900 - i, 1));
            }
            assertThat(book.snapshot(3).bids()).extracting(LevelSnapshot::price)
                    .containsExactly(9900L, 9899L, 9898L);
        }

        @Test
        void reflectsPartialFills() {
            book.add(orders.limit(Side.SELL, 10100, 10));
            book.fillBest(Side.SELL, 4);

            assertThat(book.snapshot(1).asks()).containsExactly(new LevelSnapshot(10100, 6, 1));
        }

        @Test
        void isImmutableAndDetachedFromBook() {
            book.add(orders.limit(Side.BUY, 9900, 10));
            BookSnapshot snap = book.snapshot(5);

            book.add(orders.limit(Side.BUY, 9900, 10)); // change the book afterwards

            assertThat(snap.bids()).containsExactly(new LevelSnapshot(9900, 10, 1));
            assertThatThrownBy(() -> snap.bids().add(new LevelSnapshot(1, 1, 1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void rejectsNonPositiveDepth() {
            assertThatIllegalArgumentException().isThrownBy(() -> book.snapshot(0));
        }
    }

    @Nested
    class Removal {

        @Test
        void removeByIdTakesOrderOutWithoutChangingStatus() {
            Order order = orders.limit(Side.BUY, 9900, 10);
            book.add(order);

            assertThat(book.remove(order.id())).containsSame(order);
            assertThat(order.status()).isEqualTo(OrderStatus.NEW);
            assertThat(book.find(order.id())).isEmpty();
            assertThat(book.isEmpty()).isTrue();
        }

        @Test
        void removeUnknownIdReturnsEmpty() {
            assertThat(book.remove(999)).isEmpty();
        }

        @Test
        void removingLastOrderAtPriceRemovesTheLevel() {
            Order best = orders.limit(Side.BUY, 9900, 10);
            book.add(best);
            book.add(orders.limit(Side.BUY, 9800, 10));

            book.remove(best.id());

            assertThat(book.bestBid()).hasValue(9800);
            assertThat(book.snapshot(10).bids()).hasSize(1);
        }

        @Test
        void removingOneOfSeveralOrdersKeepsLevel() {
            Order a = orders.limit(Side.BUY, 9900, 10);
            book.add(a);
            book.add(orders.limit(Side.BUY, 9900, 5));

            book.remove(a.id());

            assertThat(book.snapshot(10).bids()).containsExactly(new LevelSnapshot(9900, 5, 1));
        }

        @Test
        void fullyFillingBestRemovesOrderAndLevel() {
            Order ask = orders.limit(Side.SELL, 10100, 10);
            book.add(ask);
            book.add(orders.limit(Side.SELL, 10200, 10));

            assertThat(book.fillBest(Side.SELL, 10)).isSameAs(ask);

            assertThat(book.find(ask.id())).isEmpty();
            assertThat(book.bestAsk()).hasValue(10200);
            assertThat(book.orderCount()).isEqualTo(1);
        }

        @Test
        void partiallyFillingBestKeepsOrderIndexed() {
            Order ask = orders.limit(Side.SELL, 10100, 10);
            book.add(ask);

            book.fillBest(Side.SELL, 3);

            assertThat(book.find(ask.id())).containsSame(ask);
            assertThat(book.peekBest(Side.SELL)).containsSame(ask);
        }

        @Test
        void fillBestOnEmptySideThrows() {
            assertThatIllegalStateException().isThrownBy(() -> book.fillBest(Side.BUY, 1));
        }
    }

    @Nested
    class Validation {

        @Test
        void findsOrderById() {
            Order order = orders.limit(Side.SELL, 10100, 1);
            book.add(order);
            assertThat(book.find(order.id())).containsSame(order);
        }

        @Test
        void rejectsDuplicateId() {
            Order order = orders.limit(Side.BUY, 9900, 1);
            book.add(order);
            assertThatIllegalArgumentException().isThrownBy(() -> book.add(order));
        }

        @Test
        void rejectsOtherSymbol() {
            Order infy = new Order(50, 50, OrderFixtures.T0,
                    new OrderRequest("INFY", "T1", Side.BUY, OrderType.LIMIT, 100, 1));
            assertThatIllegalArgumentException().isThrownBy(() -> book.add(infy));
        }

        @Test
        void rejectsMarketOrder() {
            Order market = orders.of(OrderType.MARKET, Side.BUY, 0, 1);
            assertThatIllegalArgumentException().isThrownBy(() -> book.add(market));
        }

        @Test
        void rejectsInactiveOrder() {
            Order cancelled = orders.limit(Side.BUY, 9900, 1);
            cancelled.cancel();
            assertThatIllegalArgumentException().isThrownBy(() -> book.add(cancelled));
        }

        @Test
        void refusesOrdersThatWouldCross() {
            book.add(orders.limit(Side.BUY, 10000, 1));
            book.add(orders.limit(Side.SELL, 10100, 1));

            for (Order crossing : List.of(
                    orders.limit(Side.BUY, 10100, 1),     // touches best ask
                    orders.limit(Side.SELL, 10000, 1))) { // touches best bid
                assertThatIllegalArgumentException().isThrownBy(() -> book.add(crossing))
                        .withMessageContaining("cross");
            }
            assertThat(book.orderCount()).isEqualTo(2);
        }

        @Test
        void rejectsBlankSymbol() {
            assertThatIllegalArgumentException().isThrownBy(() -> new OrderBook(" "));
        }
    }
}
