package com.dalalx.core.bots;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.events.EventBus;
import com.dalalx.core.exchange.Exchange;
import com.dalalx.core.exchange.ListedStock;
import com.dalalx.core.marketdata.TradeHistory;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** All four bots trading on a real, multi-threaded exchange. */
class BotsIntegrationTest {

    private final EventBus bus = new EventBus();
    private final Exchange exchange = new Exchange(bus);
    private final LiveMarketView market = new LiveMarketView(exchange.stocks());
    private final BotLedger ledger = new BotLedger(market);
    private final TradeHistory history = new TradeHistory();

    @AfterEach
    void stop() throws InterruptedException {
        exchange.shutdown(5, TimeUnit.SECONDS);
    }

    @Test
    void botsMakeALivelyButSaneMarket() {
        bus.register(market);
        bus.register(ledger);
        bus.register(history);
        exchange.start();
        BotRunner runner = new BotRunner(StandardBots.create(), market, new ExchangeGateway(exchange), new Random(42), 50);

        for (int i = 0; i < 400; i++) {
            runner.tick();
        }

        for (ListedStock stock : exchange.stocks()) {
            String s = stock.symbol();
            BookSnapshot book = exchange.snapshot(s, 1).join();
            assertThat(book.bids()).as("%s has bids", s).isNotEmpty();
            assertThat(book.asks()).as("%s has asks", s).isNotEmpty();
            assertThat(book.bids().get(0).price()).as("%s not crossed", s).isLessThan(book.asks().get(0).price());
            long last = market.lastPrice(s);
            assertThat(Math.abs(Ticks.changeBp(stock.referencePrice(), last))).as("%s within 10%%", s).isLessThan(1000);
            assertThat(history.recent(s, 1)).as("%s traded", s).isNotEmpty();
        }
        // Every share bought was sold by someone: positions across all traders sum to zero.
        long net = StandardBots.create().stream().map(b -> ledger.stats(b.id()))
                .flatMap(st -> st.positions().values().stream()).mapToLong(Long::longValue).sum();
        assertThat(net).isZero();
        assertThat(ledger.stats(MarketMakerBot.ID).trades()).isPositive();
    }
}
