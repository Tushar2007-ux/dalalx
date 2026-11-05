package com.dalalx.core.soak;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.bots.BotLedger;
import com.dalalx.core.bots.BotRunner;
import com.dalalx.core.bots.BotStats;
import com.dalalx.core.bots.ExchangeGateway;
import com.dalalx.core.bots.LiveMarketView;
import com.dalalx.core.bots.SpooferBot;
import com.dalalx.core.bots.StandardBots;
import com.dalalx.core.bots.TradingBot;
import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.breaker.MarketPhase;
import com.dalalx.core.breaker.StatusBoard;
import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.EngineListener;
import com.dalalx.core.events.EventBus;
import com.dalalx.core.exchange.Exchange;
import com.dalalx.core.exchange.ListedStock;
import com.dalalx.core.headlines.HeadlineWriter;
import com.dalalx.core.scenarios.ScenarioDirector;
import com.dalalx.core.scenarios.Storylines;
import com.dalalx.core.support.MutableClock;
import com.dalalx.core.surveillance.Case;
import com.dalalx.core.surveillance.Suspensions;
import com.dalalx.core.surveillance.SurveillanceDesk;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Everything at once, for a few simulated minutes: all five bots, every
 * storyline in turn, the breaker, the auction, surveillance with a
 * suspension, and the newsroom. Then the invariants that must always hold.
 */
class SoakTest {

    private final EventBus bus = new EventBus();
    private final Exchange exchange = new Exchange(bus);

    @AfterEach
    void stop() throws InterruptedException {
        exchange.shutdown(10, TimeUnit.SECONDS);
    }

    @Test
    void theWholeMarketStaysConsistentUnderLoad() {
        LiveMarketView market = new LiveMarketView(exchange.stocks());
        BotLedger ledger = new BotLedger(market);
        StatusBoard board = new StatusBoard();
        Suspensions suspensions = new Suspensions();
        SurveillanceDesk desk = new SurveillanceDesk(SurveillanceDesk.standardRules(), suspensions,
                trader -> exchange.cancelAllFor(trader).join());
        AtomicInteger listenerFailures = new AtomicInteger();
        EngineListener canary = e -> checkEvent(e, listenerFailures);
        for (EngineListener l : new EngineListener[] {market, ledger, board, desk, canary}) {
            bus.register(l);
        }
        exchange.guardWith(r -> suspensions.refusal(r.traderId()));
        exchange.start();

        MutableClock clock = new MutableClock(Instant.now());
        BotRunner runner = new BotRunner(StandardBots.create(), market, new ExchangeGateway(exchange), new Random(11), 250);
        ScenarioDirector director = new ScenarioDirector(Storylines.all(), runner, clock);
        HeadlineWriter news = new HeadlineWriter(s -> exchange.stock(s).map(ListedStock::referencePrice).orElseThrow(),
                runner::nameOf);
        bus.register(news);
        director.addListener(news);
        desk.addListener(news);
        runner.addHook(director);

        for (String story : new String[] {"flash-crash", "bubble", "whale-dump"}) {
            director.start(story);
            for (int i = 0; i < 400; i++) {
                clock.advance(Duration.ofMillis(250));
                runner.tick();
            }
            Case spoof = desk.cases().stream().filter(c -> c.traderId().equals(SpooferBot.ID)
                    && c.status() == Case.Status.OPEN).findFirst().orElse(null);
            if (spoof != null) {
                desk.suspend(spoof.id());
            }
        }

        assertThat(listenerFailures).hasValue(0);
        // Every share bought was sold by someone.
        long net = runner.bots().stream().map(TradingBot::id).map(ledger::stats)
                .flatMap(s -> s.positions().values().stream()).mapToLong(Long::longValue).sum();
        assertThat(net).isZero();
        assertThat(runner.bots().stream().map(TradingBot::id).map(ledger::stats).mapToLong(BotStats::trades).sum())
                .isGreaterThan(1_000);
        // Outside an auction, no book is ever crossed.
        for (ListedStock stock : exchange.stocks()) {
            BookSnapshot book = exchange.snapshot(stock.symbol(), 1).join();
            if (board.status(stock.symbol()).phase() != MarketPhase.AUCTION && !book.bids().isEmpty() && !book.asks().isEmpty()) {
                assertThat(book.bids().get(0).price()).as(stock.symbol()).isLessThan(book.asks().get(0).price());
            }
        }
        assertThat(suspensions.isSuspended(SpooferBot.ID)).isTrue();
        assertThat(news.latest(50)).isNotEmpty();
    }

    /** A listener that fails the test (by counting) if any event breaks a basic rule. */
    private static void checkEvent(EngineEvent e, AtomicInteger failures) {
        if (e.sequence() <= 0 || e.timestamp() == null || e.symbol() == null) {
            failures.incrementAndGet();
        }
    }
}
