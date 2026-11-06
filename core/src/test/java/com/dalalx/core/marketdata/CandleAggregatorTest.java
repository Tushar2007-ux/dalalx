package com.dalalx.core.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CandleAggregatorTest {

    private static final Instant T = Instant.parse("2026-11-04T10:00:00Z");
    private final CandleAggregator candles = new CandleAggregator();
    private final AtomicLong ids = new AtomicLong();

    private Trade trade(String symbol, long secondsAfter, long price, long qty) {
        long id = ids.addAndGet(3);
        return new Trade(id, symbol, price, qty, id + 1, id + 2, "B", "S", Side.BUY, T.plusSeconds(secondsAfter));
    }

    @Test
    void tradesInOneMinuteBuildOneOhlcvCandle() {
        candles.add(trade("TCS", 1, 409000, 10));
        candles.add(trade("TCS", 20, 409500, 5));
        candles.add(trade("TCS", 40, 408500, 7));
        candles.add(trade("TCS", 59, 409200, 3));

        assertThat(candles.candles("TCS", CandleInterval.ONE_MINUTE)).singleElement().satisfies(c -> {
            assertThat(c.startMillis()).isEqualTo(T.toEpochMilli());
            assertThat(c.open()).isEqualTo(409000);
            assertThat(c.high()).isEqualTo(409500);
            assertThat(c.low()).isEqualTo(408500);
            assertThat(c.close()).isEqualTo(409200);
            assertThat(c.volume()).isEqualTo(25);
        });
    }

    @Test
    void aNewMinuteStartsANewCandleButTheFiveMinuteCandleKeepsGrowing() {
        candles.add(trade("TCS", 10, 409000, 10));
        candles.add(trade("TCS", 70, 410000, 10));

        assertThat(candles.candles("TCS", CandleInterval.ONE_MINUTE)).hasSize(2);
        assertThat(candles.candles("TCS", CandleInterval.FIVE_MINUTES)).singleElement()
                .satisfies(c -> assertThat(c.volume()).isEqualTo(20));
    }

    @Test
    void symbolsAreKeptApart() {
        candles.add(trade("TCS", 1, 409000, 10));
        candles.add(trade("INFY", 1, 154000, 4));

        assertThat(candles.candles("INFY", CandleInterval.ONE_MINUTE)).singleElement()
                .satisfies(c -> assertThat(c.open()).isEqualTo(154000));
        assertThat(candles.candles("HDFCBANK", CandleInterval.ONE_MINUTE)).isEmpty();
    }

    @Test
    void historyIsBounded() {
        for (int minute = 0; minute < CandleAggregator.MAX_CANDLES + 20; minute++) {
            candles.add(trade("TCS", minute * 60L, 409000, 1));
        }

        assertThat(candles.candles("TCS", CandleInterval.ONE_MINUTE)).hasSize(CandleAggregator.MAX_CANDLES);
    }

    @Test
    void intervalCodesRoundTripAndUnknownCodesAreRejected() {
        assertThat(CandleInterval.fromCode("15m")).isEqualTo(CandleInterval.FIFTEEN_MINUTES);
        assertThat(CandleInterval.fromCode("1D").code()).isEqualTo("1D");
        assertThat(CandleInterval.fromCode("10s").bucketStart(25_000)).isEqualTo(20_000); // ten-second candles
        assertThatIllegalArgumentException().isThrownBy(() -> CandleInterval.fromCode("2h"));
    }
}
