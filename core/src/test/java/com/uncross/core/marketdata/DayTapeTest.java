package com.uncross.core.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.uncross.core.model.Side;
import com.uncross.core.model.Trade;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The day's figures for the evening paper. */
class DayTapeTest {

    private final DayTape tape = new DayTape();
    private long ids;

    private void trade(String symbol, long price, long qty) {
        tape.add(new Trade(++ids, symbol, price, qty, ++ids, ++ids, "B", "S", Side.BUY, Instant.EPOCH));
    }

    @Test
    void eachStockGetsItsOpenHighLowLastAndVolume() {
        trade("TCS", 400_000, 10);
        trade("TCS", 410_000, 5);
        trade("TCS", 395_000, 20);
        trade("TCS", 402_000, 1);

        DayTape.StockDay tcs = tape.summary().stocks().get(0);
        assertThat(tcs).isEqualTo(new DayTape.StockDay("TCS", 400_000, 410_000, 395_000, 402_000, 36,
                400_000L * 10 + 410_000L * 5 + 395_000L * 20 + 402_000, 4));
    }

    @Test
    void itKeepsOnlyTheFiveBiggestTradesByValue() {
        for (int i = 1; i <= 8; i++) {
            trade("INFY", 100_000, i * 10L);
        }
        trade("TCS", 400_000, 1);

        assertThat(tape.summary().biggest()).extracting(DayTape.BigTrade::qty).containsExactly(80L, 70L, 60L, 50L, 40L);
        assertThat(tape.summary().trades()).isEqualTo(9);
    }
}
