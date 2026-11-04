package com.dalalx.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderTypeTest {

    @Test
    void limitAndIocNeedAPriceButMarketDoesNot() {
        assertThat(OrderType.LIMIT.requiresLimitPrice()).isTrue();
        assertThat(OrderType.IOC.requiresLimitPrice()).isTrue();
        assertThat(OrderType.MARKET.requiresLimitPrice()).isFalse();
    }
}
