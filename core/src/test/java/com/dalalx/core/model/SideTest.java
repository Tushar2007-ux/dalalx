package com.dalalx.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SideTest {

    @Test
    void buyIsOppositeOfSell() {
        assertThat(Side.BUY.opposite()).isEqualTo(Side.SELL);
        assertThat(Side.SELL.opposite()).isEqualTo(Side.BUY);
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    void oppositeOfOppositeIsItself(Side side) {
        assertThat(side.opposite().opposite()).isEqualTo(side);
    }
}
