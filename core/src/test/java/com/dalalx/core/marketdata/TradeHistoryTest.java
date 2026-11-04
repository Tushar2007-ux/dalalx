package com.dalalx.core.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TradeHistoryTest {

    private Trade trade(long id, long price) {
        return new Trade(id, "TCS", price, 1, id * 10, id * 10 + 1, "B", "S", Side.BUY, Instant.EPOCH);
    }

    @Test
    void returnsNewestFirstAndRemembersTheLastPrice() {
        TradeHistory history = new TradeHistory();
        history.add(trade(1, 100));
        history.add(trade(2, 200));

        assertThat(history.recent("TCS", 10)).extracting(Trade::id).containsExactly(2L, 1L);
        assertThat(history.lastPrice("TCS")).hasValue(200);
        assertThat(history.lastPrice("INFY")).isEmpty();
    }

    @Test
    void dropsTheOldestTradesBeyondCapacity() {
        TradeHistory history = new TradeHistory(3);
        for (long id = 1; id <= 5; id++) {
            history.add(trade(id, 100));
        }

        assertThat(history.recent("TCS", 10)).extracting(Trade::id).containsExactly(5L, 4L, 3L);
        assertThat(history.recent("TCS", 2)).hasSize(2);
    }
}
