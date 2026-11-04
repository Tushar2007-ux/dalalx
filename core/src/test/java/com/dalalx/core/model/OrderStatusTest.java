package com.dalalx.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void onlyFilledCancelledAndRejectedAreTerminal() {
        assertThat(OrderStatus.NEW.isTerminal()).isFalse();
        assertThat(OrderStatus.PARTIALLY_FILLED.isTerminal()).isFalse();
        assertThat(OrderStatus.FILLED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.REJECTED.isTerminal()).isTrue();
    }
}
