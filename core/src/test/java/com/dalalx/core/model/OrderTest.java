package com.dalalx.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the {@link Order} lifecycle. Each legal transition in the
 * {@link OrderStatus} table has its own test; illegal ones are parameterized
 * over every status they're illegal from.
 */
class OrderTest {

    private static final Instant NOW = Instant.parse("2026-11-04T09:15:00Z");
    private static final long QTY = 100;

    private static Order newOrder() {
        OrderRequest request = new OrderRequest("RELIANCE", "T1", Side.BUY, OrderType.LIMIT, 297780, QTY);
        return new Order(1, 7, NOW, request);
    }

    /** Builds an order that has legally reached {@code status}. */
    private static Order orderIn(OrderStatus status) {
        Order order = newOrder();
        switch (status) {
            case NEW -> { }
            case PARTIALLY_FILLED -> order.fill(40);
            case FILLED -> order.fill(QTY);
            case CANCELLED -> order.cancel();
            case REJECTED -> order.reject();
        }
        assertThat(order.status()).isEqualTo(status);
        return order;
    }

    @Nested
    class Creation {

        @Test
        void copiesRequestAndStartsNew() {
            Order order = newOrder();

            assertThat(order.id()).isEqualTo(1);
            assertThat(order.sequence()).isEqualTo(7);
            assertThat(order.timestamp()).isEqualTo(NOW);
            assertThat(order.symbol()).isEqualTo("RELIANCE");
            assertThat(order.traderId()).isEqualTo("T1");
            assertThat(order.side()).isEqualTo(Side.BUY);
            assertThat(order.type()).isEqualTo(OrderType.LIMIT);
            assertThat(order.limitPrice()).isEqualTo(297780);
            assertThat(order.originalQty()).isEqualTo(QTY);
            assertThat(order.remainingQty()).isEqualTo(QTY);
            assertThat(order.filledQty()).isZero();
            assertThat(order.status()).isEqualTo(OrderStatus.NEW);
            assertThat(order.isActive()).isTrue();
        }

        @Test
        void marketOrderHasZeroLimitPrice() {
            OrderRequest request = new OrderRequest("TCS", "T1", Side.SELL, OrderType.MARKET, 500, 3);
            assertThat(new Order(2, 0, NOW, request).limitPrice()).isZero();
        }

        @Test
        void rejectsNegativeSequence() {
            OrderRequest request = new OrderRequest("TCS", "T1", Side.SELL, OrderType.LIMIT, 500, 3);
            assertThatIllegalArgumentException().isThrownBy(() -> new Order(2, -1, NOW, request));
        }

        @Test
        void rejectsNullRequestOrTimestamp() {
            OrderRequest request = new OrderRequest("TCS", "T1", Side.SELL, OrderType.LIMIT, 500, 3);
            assertThatNullPointerException().isThrownBy(() -> new Order(2, 0, NOW, null));
            assertThatNullPointerException().isThrownBy(() -> new Order(2, 0, null, request));
        }
    }

    @Nested
    class LegalTransitions {

        @Test
        void newToPartiallyFilled() {
            Order order = newOrder();
            order.fill(30);

            assertThat(order.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(order.remainingQty()).isEqualTo(70);
            assertThat(order.filledQty()).isEqualTo(30);
            assertThat(order.isActive()).isTrue();
        }

        @Test
        void newToFilled() {
            Order order = newOrder();
            order.fill(QTY);

            assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
            assertThat(order.remainingQty()).isZero();
            assertThat(order.filledQty()).isEqualTo(QTY);
            assertThat(order.isActive()).isFalse();
        }

        @Test
        void newToCancelled() {
            Order order = newOrder();
            order.cancel();

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.remainingQty()).isEqualTo(QTY);
            assertThat(order.isActive()).isFalse();
        }

        @Test
        void newToRejected() {
            Order order = newOrder();
            order.reject();

            assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
            assertThat(order.isActive()).isFalse();
        }

        @Test
        void partiallyFilledStaysPartiallyFilledOnAnotherPartFill() {
            Order order = orderIn(OrderStatus.PARTIALLY_FILLED); // 40 filled
            order.fill(10);

            assertThat(order.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(order.remainingQty()).isEqualTo(50);
            assertThat(order.filledQty()).isEqualTo(50);
        }

        @Test
        void partiallyFilledToFilled() {
            Order order = orderIn(OrderStatus.PARTIALLY_FILLED);
            order.fill(60);

            assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
            assertThat(order.remainingQty()).isZero();
        }

        @Test
        void partiallyFilledToCancelledKeepsFilledQty() {
            Order order = orderIn(OrderStatus.PARTIALLY_FILLED);
            order.cancel();

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.filledQty()).isEqualTo(40);
            assertThat(order.remainingQty()).isEqualTo(60); // the cancelled part
        }

        @Test
        void manySmallFillsEndInFilled() {
            Order order = newOrder();
            for (int i = 0; i < QTY; i++) {
                order.fill(1);
            }
            assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        }
    }

    @Nested
    class IllegalTransitions {

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"FILLED", "CANCELLED", "REJECTED"})
        void cannotFillTerminalOrder(OrderStatus status) {
            Order order = orderIn(status);
            assertThatIllegalStateException().isThrownBy(() -> order.fill(1))
                    .withMessageContaining(status.name());
            assertThat(order.status()).isEqualTo(status);
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"FILLED", "CANCELLED", "REJECTED"})
        void cannotCancelTerminalOrder(OrderStatus status) {
            Order order = orderIn(status);
            assertThatIllegalStateException().isThrownBy(order::cancel);
            assertThat(order.status()).isEqualTo(status);
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PARTIALLY_FILLED", "FILLED", "CANCELLED", "REJECTED"})
        void canOnlyRejectNewOrder(OrderStatus status) {
            Order order = orderIn(status);
            assertThatIllegalStateException().isThrownBy(order::reject);
            assertThat(order.status()).isEqualTo(status);
        }
    }

    @Nested
    class InvalidFillQuantity {

        @ParameterizedTest
        @ValueSource(longs = {0, -1, Long.MIN_VALUE})
        void rejectsNonPositiveFill(long qty) {
            Order order = newOrder();
            assertThatIllegalArgumentException().isThrownBy(() -> order.fill(qty));
            assertUnchanged(order, OrderStatus.NEW, QTY);
        }

        @Test
        void rejectsOverfillOnNewOrder() {
            Order order = newOrder();
            assertThatIllegalArgumentException().isThrownBy(() -> order.fill(QTY + 1));
            assertUnchanged(order, OrderStatus.NEW, QTY);
        }

        @Test
        void rejectsOverfillOnPartiallyFilledOrder() {
            Order order = orderIn(OrderStatus.PARTIALLY_FILLED); // 60 remain
            assertThatIllegalArgumentException().isThrownBy(() -> order.fill(61));
            assertUnchanged(order, OrderStatus.PARTIALLY_FILLED, 60);
        }

        private void assertUnchanged(Order order, OrderStatus status, long remaining) {
            assertThat(order.status()).isEqualTo(status);
            assertThat(order.remainingQty()).isEqualTo(remaining);
        }
    }

    @Nested
    class Identity {

        @Test
        void ordersWithSameIdAreEqualEvenIfStateDiffers() {
            Order a = newOrder();
            Order b = newOrder();
            b.fill(10);
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void ordersWithDifferentIdsAreNotEqual() {
            OrderRequest request = new OrderRequest("TCS", "T1", Side.BUY, OrderType.LIMIT, 100, 1);
            assertThat(new Order(1, 0, NOW, request)).isNotEqualTo(new Order(2, 0, NOW, request));
        }

        @Test
        void toStringShowsPriceInRupees() {
            assertThat(newOrder().toString()).contains("₹2,977.80", "RELIANCE", "NEW");
        }
    }
}
