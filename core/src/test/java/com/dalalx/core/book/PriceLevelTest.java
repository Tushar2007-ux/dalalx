package com.dalalx.core.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderFixtures;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.Side;
import org.junit.jupiter.api.Test;

class PriceLevelTest {

    private final OrderFixtures orders = new OrderFixtures();
    private final PriceLevel level = new PriceLevel(10000);

    @Test
    void newLevelIsEmpty() {
        assertThat(level.isEmpty()).isTrue();
        assertThat(level.totalQty()).isZero();
        assertThat(level.orderCount()).isZero();
        assertThat(level.peekOldest()).isNull();
    }

    @Test
    void tracksTotalQtyAndCountAsOrdersJoin() {
        level.add(orders.limit(Side.BUY, 10000, 30));
        level.add(orders.limit(Side.BUY, 10000, 70));

        assertThat(level.totalQty()).isEqualTo(100);
        assertThat(level.orderCount()).isEqualTo(2);
        assertThat(level.toSnapshot()).isEqualTo(new LevelSnapshot(10000, 100, 2));
    }

    @Test
    void isFifo() {
        Order first = orders.limit(Side.BUY, 10000, 10);
        Order second = orders.limit(Side.BUY, 10000, 10);
        level.add(first);
        level.add(second);

        assertThat(level.peekOldest()).isSameAs(first);
        level.fillOldest(10);
        assertThat(level.peekOldest()).isSameAs(second);
    }

    @Test
    void partialFillKeepsOrderAtFront() {
        Order first = orders.limit(Side.BUY, 10000, 10);
        level.add(first);
        level.add(orders.limit(Side.BUY, 10000, 10));

        level.fillOldest(4);

        assertThat(level.peekOldest()).isSameAs(first);
        assertThat(first.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(level.totalQty()).isEqualTo(16);
        assertThat(level.orderCount()).isEqualTo(2);
    }

    @Test
    void fullFillRemovesOrder() {
        Order only = orders.limit(Side.BUY, 10000, 10);
        level.add(only);

        assertThat(level.fillOldest(10)).isSameAs(only);
        assertThat(level.isEmpty()).isTrue();
        assertThat(level.totalQty()).isZero();
    }

    @Test
    void invalidFillLeavesTotalUnchanged() {
        level.add(orders.limit(Side.BUY, 10000, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> level.fillOldest(11));
        assertThat(level.totalQty()).isEqualTo(10);
    }

    @Test
    void removeSubtractsRemainingQty() {
        Order a = orders.limit(Side.BUY, 10000, 10);
        Order b = orders.limit(Side.BUY, 10000, 20);
        level.add(a);
        level.add(b);
        level.fillOldest(3); // a now has 7 remaining

        assertThat(level.remove(a)).isTrue();
        assertThat(level.totalQty()).isEqualTo(20);
        assertThat(level.remove(a)).isFalse();
        assertThat(level.totalQty()).isEqualTo(20);
    }

    @Test
    void rejectsOrderAtDifferentPrice() {
        assertThatIllegalArgumentException().isThrownBy(() -> level.add(orders.limit(Side.BUY, 10001, 1)));
    }
}
