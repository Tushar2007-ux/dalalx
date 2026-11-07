package com.dalalx.core.audience;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.audience.Entrant.Kind;
import com.dalalx.core.bots.BotLedger;
import com.dalalx.core.bots.LiveMarketView;
import com.dalalx.core.exchange.ListedStock;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeaderboardTest {

    private final LiveMarketView market = new LiveMarketView(List.of(new ListedStock("TCS", "TCS", 400000)));
    private final BotLedger ledger = new BotLedger(market);

    private void trade(String buyer, String seller, long price, long qty) {
        ledger.record(new Trade(1, "TCS", price, qty, 1, 2, buyer, seller, Side.BUY, Instant.EPOCH));
        market.recordTrade("TCS", price);
    }

    @Test
    void ranksEveryoneByProfitBestFirst() {
        trade("GUEST-1", "MUNIM", 400000, 10); // the guest buys 10
        trade("YOU", "MUNIM", 401000, 1);      // the price rises ₹10: guest +₹100, munim -₹110

        List<Standing> board = Leaderboard.rank(List.of(
                new Entrant("YOU", "You", Kind.PRESENTER),
                new Entrant("MUNIM", "The Munim", Kind.BOT),
                new Entrant("GUEST-1", "Richa", Kind.AUDIENCE)), ledger);

        assertThat(board).extracting(Standing::rank, Standing::name, Standing::pnl).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "Richa", 10_000L),
                org.assertj.core.groups.Tuple.tuple(2, "You", 0L),
                org.assertj.core.groups.Tuple.tuple(3, "The Munim", -10_000L));
        assertThat(board.get(0).kind()).isEqualTo(Kind.AUDIENCE);
    }

    @Test
    void tiesGoToMoreTradesThenName() {
        trade("B", "A", 400000, 1);

        List<Standing> board = Leaderboard.rank(List.of(
                new Entrant("Z", "Zed", Kind.AUDIENCE),
                new Entrant("C", "Cee", Kind.AUDIENCE),
                new Entrant("A", "Ay", Kind.BOT)), ledger);

        assertThat(board).extracting(Standing::name).containsExactly("Ay", "Cee", "Zed");
    }
}
