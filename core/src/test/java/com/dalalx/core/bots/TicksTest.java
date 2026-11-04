package com.dalalx.core.bots;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicksTest {

    @Test
    void roundsToFivePaiseHalfUp() {
        assertThat(Ticks.round(246002)).isEqualTo(246000);
        assertThat(Ticks.round(246003)).isEqualTo(246005);
        assertThat(Ticks.round(1)).isEqualTo(Ticks.TICK); // never zero or negative
    }

    @Test
    void movesByBasisPointsWithIntegerMaths() {
        assertThat(Ticks.moveBy(100000, 50)).isEqualTo(100500);   // +0.5%
        assertThat(Ticks.moveBy(100000, -1000)).isEqualTo(90000); // -10%
        assertThat(Ticks.changeBp(100000, 99000)).isEqualTo(-100);
        assertThat(Ticks.changeBp(0, 5)).isZero();
    }
}
