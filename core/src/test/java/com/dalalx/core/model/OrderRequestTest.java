package com.dalalx.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderRequestTest {

    private static OrderRequest limit(long price, long qty) {
        return new OrderRequest("TCS", "T1", Side.BUY, OrderType.LIMIT, price, qty);
    }

    @Test
    void validLimitRequestKeepsAllFields() {
        OrderRequest request = new OrderRequest("TCS", "T1", Side.SELL, OrderType.LIMIT, 10250, 5);

        assertThat(request.symbol()).isEqualTo("TCS");
        assertThat(request.traderId()).isEqualTo("T1");
        assertThat(request.side()).isEqualTo(Side.SELL);
        assertThat(request.type()).isEqualTo(OrderType.LIMIT);
        assertThat(request.price()).isEqualTo(10250);
        assertThat(request.qty()).isEqualTo(5);
    }

    @Test
    void validIocRequestIsAccepted() {
        OrderRequest request = new OrderRequest("INFY", "T1", Side.BUY, OrderType.IOC, 1, 1);
        assertThat(request.price()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -10250})
    void marketRequestIgnoresAnyPriceAndStoresZero(long price) {
        OrderRequest request = new OrderRequest("TCS", "T1", Side.BUY, OrderType.MARKET, price, 10);
        assertThat(request.price()).isZero();
    }

    @Test
    void marketRequestNormalisesPositivePriceToZero() {
        OrderRequest request = new OrderRequest("TCS", "T1", Side.BUY, OrderType.MARKET, 99999, 10);
        assertThat(request.price()).isZero();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonPositiveQty(long qty) {
        assertThatIllegalArgumentException().isThrownBy(() -> limit(10250, qty))
                .withMessageContaining("qty");
    }

    @ParameterizedTest
    @EnumSource(value = OrderType.class, names = {"LIMIT", "IOC"})
    void priceTypesRejectZeroPrice(OrderType type) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OrderRequest("TCS", "T1", Side.BUY, type, 0, 10))
                .withMessageContaining("price");
    }

    @ParameterizedTest
    @EnumSource(value = OrderType.class, names = {"LIMIT", "IOC"})
    void priceTypesRejectNegativePrice(OrderType type) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OrderRequest("TCS", "T1", Side.BUY, type, -100, 10));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "  \n"})
    void rejectsBlankSymbol(String symbol) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OrderRequest(symbol, "T1", Side.BUY, OrderType.LIMIT, 100, 1))
                .withMessageContaining("symbol");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsBlankTraderId(String traderId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OrderRequest("TCS", traderId, Side.BUY, OrderType.LIMIT, 100, 1))
                .withMessageContaining("traderId");
    }

    @Test
    void rejectsNullSide() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OrderRequest("TCS", "T1", null, OrderType.LIMIT, 100, 1))
                .withMessageContaining("side");
    }

    @Test
    void rejectsNullType() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OrderRequest("TCS", "T1", Side.BUY, null, 100, 1))
                .withMessageContaining("type");
    }

    @Test
    void requestsWithSameValuesAreEqual() {
        // Records get value-based equals/hashCode for free.
        assertThat(limit(100, 1)).isEqualTo(limit(100, 1)).hasSameHashCodeAs(limit(100, 1));
    }
}
