package com.dalalx.core.surveillance;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.bots.BotRunner;
import com.dalalx.core.bots.ExchangeGateway;
import com.dalalx.core.bots.LiveMarketView;
import com.dalalx.core.bots.SpooferBot;
import com.dalalx.core.bots.StandardBots;
import com.dalalx.core.book.LevelSnapshot;
import com.dalalx.core.events.EventBus;
import com.dalalx.core.events.OrderRejected;
import com.dalalx.core.events.RecordingListener;
import com.dalalx.core.exchange.Exchange;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The Inspector catches The Spoofer on a live market, and suspension takes it off the floor. */
class SurveillanceIntegrationTest {

    private final EventBus bus = new EventBus();
    private final Exchange exchange = new Exchange(bus);
    private final Suspensions suspensions = new Suspensions();
    private final SurveillanceDesk desk = new SurveillanceDesk(SurveillanceDesk.standardRules(), suspensions,
            trader -> exchange.cancelAllFor(trader).join());

    @AfterEach
    void stop() throws InterruptedException {
        exchange.shutdown(5, TimeUnit.SECONDS);
    }

    @Test
    void theSpooferIsCaughtAndSuspended() {
        LiveMarketView market = new LiveMarketView(exchange.stocks());
        RecordingListener events = new RecordingListener();
        bus.register(market);
        bus.register(desk);
        bus.register(events);
        exchange.guardWith(request -> suspensions.refusal(request.traderId()));
        exchange.start();
        BotRunner runner = new BotRunner(StandardBots.create(), market, new ExchangeGateway(exchange), new Random(3), 250);

        for (int i = 0; i < 120 && desk.cases().stream().noneMatch(c -> c.traderId().equals(SpooferBot.ID)); i++) {
            runner.tick();
        }
        Case caught = desk.cases().stream().filter(c -> c.traderId().equals(SpooferBot.ID)).findFirst().orElseThrow();
        assertThat(caught.ruleId()).isIn("spoofing", "layering");

        desk.suspend(caught.id());
        events.clear();
        for (int i = 0; i < 40; i++) {
            runner.tick();
        }

        assertThat(events.eventsOf(OrderRejected.class)).isNotEmpty()
                .allSatisfy(r -> {
                    if (r.order().traderId().equals(SpooferBot.ID)) {
                        assertThat(r.reason()).isEqualTo("trader suspended by market surveillance");
                    }
                });
        for (var stock : exchange.stocks()) {
            var book = exchange.snapshot(stock.symbol(), 50).join();
            assertThat(book.bids()).extracting(LevelSnapshot::mainTrader).doesNotContain(SpooferBot.ID);
        }
    }
}
