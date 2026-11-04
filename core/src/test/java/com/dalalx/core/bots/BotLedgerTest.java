package com.dalalx.core.bots;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BotLedgerTest {

    private final FakeMarket market = new FakeMarket().with("TCS", 400000, 400000);
    private final BotLedger ledger = new BotLedger(market);

    private static Trade trade(String buyer, String seller, long price, long qty) {
        return new Trade(1, "TCS", price, qty, 1, 2, buyer, seller, Side.BUY, Instant.EPOCH);
    }

    @Test
    void movesSharesToTheBuyerAndCashToTheSeller() {
        ledger.record(trade("WHALE", "MUNIM", 400000, 10));

        assertThat(ledger.stats("WHALE")).extracting(BotStats::cash, BotStats::positions, BotStats::trades)
                .containsExactly(-4_000_000L, Map.of("TCS", 10L), 1L);
        assertThat(ledger.stats("MUNIM").positions()).containsEntry("TCS", -10L);
    }

    @Test
    void marksPositionsToTheLastPrice() {
        ledger.record(trade("WHALE", "MUNIM", 400000, 10));
        market.with("TCS", 400000, 410000); // price rises ₹100

        assertThat(ledger.stats("WHALE").pnl()).isEqualTo(100_000); // +₹1,000 on 10 shares
        assertThat(ledger.stats("MUNIM").pnl()).isEqualTo(-100_000);
    }

    @Test
    void aRoundTripRealisesTheProfitAndFlattensThePosition() {
        ledger.record(trade("MOMENTUM", "MUNIM", 400000, 10));
        ledger.record(trade("MUNIM", "MOMENTUM", 400500, 10));

        BotStats momentum = ledger.stats("MOMENTUM");
        assertThat(momentum.positions()).containsEntry("TCS", 0L);
        assertThat(momentum.pnl()).isEqualTo(5_000);
        assertThat(momentum.volume()).isEqualTo(20);
    }

    @Test
    void unknownTraderHasEmptyStats() {
        assertThat(ledger.stats("NOBODY")).isEqualTo(new BotStats("NOBODY", 0, 0, 0, Map.of(), 0));
    }
}
