package com.dalalx.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TradeTest {

    private static final Instant NOW = Instant.parse("2026-11-04T09:15:00Z");

    private static Trade trade(long price, long qty) {
        return new Trade(1, "INFY", price, qty, 10, 20, "BUYER", "SELLER", Side.BUY, NOW);
    }

    @Test
    void keepsAllFields() {
        Trade t = trade(150000, 5);

        assertThat(t.id()).isEqualTo(1);
        assertThat(t.symbol()).isEqualTo("INFY");
        assertThat(t.price()).isEqualTo(150000);
        assertThat(t.qty()).isEqualTo(5);
        assertThat(t.buyOrderId()).isEqualTo(10);
        assertThat(t.sellOrderId()).isEqualTo(20);
        assertThat(t.buyerId()).isEqualTo("BUYER");
        assertThat(t.sellerId()).isEqualTo("SELLER");
        assertThat(t.aggressorSide()).isEqualTo(Side.BUY);
        assertThat(t.timestamp()).isEqualTo(NOW);
    }

    @Test
    void notionalIsPriceTimesQty() {
        assertThat(trade(10250, 4).notional()).isEqualTo(41000);
    }

    @Test
    void notionalThrowsInsteadOfOverflowing() {
        Trade huge = trade(Long.MAX_VALUE, 2);
        assertThatThrownBy(huge::notional)
                .isInstanceOf(ArithmeticException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositivePrice(long price) {
        assertThatIllegalArgumentException().isThrownBy(() -> trade(price, 1));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveQty(long qty) {
        assertThatIllegalArgumentException().isThrownBy(() -> trade(100, qty));
    }

    @Test
    void rejectsSelfTrade() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Trade(1, "INFY", 100, 1, 10, 10, "A", "B", Side.BUY, NOW));
    }

    @Test
    void rejectsNullFields() {
        assertThatNullPointerException().isThrownBy(() ->
                new Trade(1, null, 100, 1, 10, 20, "A", "B", Side.BUY, NOW));
        assertThatNullPointerException().isThrownBy(() ->
                new Trade(1, "INFY", 100, 1, 10, 20, null, "B", Side.BUY, NOW));
        assertThatNullPointerException().isThrownBy(() ->
                new Trade(1, "INFY", 100, 1, 10, 20, "A", null, Side.BUY, NOW));
        assertThatNullPointerException().isThrownBy(() ->
                new Trade(1, "INFY", 100, 1, 10, 20, "A", "B", null, NOW));
        assertThatNullPointerException().isThrownBy(() ->
                new Trade(1, "INFY", 100, 1, 10, 20, "A", "B", Side.BUY, null));
    }

    @Test
    void tradesWithSameValuesAreEqual() {
        assertThat(trade(100, 1)).isEqualTo(trade(100, 1));
    }
}
