package com.uncross.server.config;

import com.uncross.core.accounts.Account;
import com.uncross.core.accounts.Accounts;
import com.uncross.core.bots.BotRunner;
import com.uncross.core.bots.LiveMarketView;
import com.uncross.core.bots.Tournament;
import com.uncross.core.headlines.Headline;
import com.uncross.core.news.NewsDesk;
import java.time.Duration;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import com.uncross.core.events.EventBus;
import com.uncross.core.exchange.Exchange;
import com.uncross.core.exchange.ListedStock;
import com.uncross.core.headlines.HeadlineWriter;
import com.uncross.core.scenarios.ScenarioDirector;
import com.uncross.core.scenarios.Storylines;
import com.uncross.core.surveillance.Suspensions;
import com.uncross.core.surveillance.SurveillanceDesk;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the storylines and the newsroom into the server. All behaviour lives
 * in core ({@code scenarios}, {@code headlines}); this class only connects the
 * observers: the director runs on the bot thread as a tick hook, the
 * surveillance desk listens to the engine, and the headline writer listens to
 * the engine, the director and the desk.
 */
@Configuration
public class NewsConfig {

    @Bean
    public ScenarioDirector scenarioDirector(BotRunner runner, Clock clock) {
        ScenarioDirector director = new ScenarioDirector(Storylines.all(), runner, clock);
        runner.addHook(director);
        return director;
    }

    @Bean
    public HeadlineWriter headlineWriter(Exchange exchange, BotRunner runner, EventBus bus, ScenarioDirector director,
                                         Accounts accounts) {
        HeadlineWriter writer = new HeadlineWriter(
                symbol -> exchange.stock(symbol).map(ListedStock::referencePrice).orElseThrow(),
                id -> "YOU".equals(id) ? "A reader"
                        : accounts.byTrader(id).map(Account::name).orElseGet(() -> runner.nameOf(id)));
        bus.register(writer);
        director.addListener(writer);
        exchange.onListing(stock -> writer.file(java.time.Instant.now(), "New listing", stock.symbol(),
                stock.symbol() + " lists on Uncross", stock.name() + " opens for trading at its reference price.",
                Headline.Tone.NEUTRAL));
        return writer;
    }

    @Bean
    public NewsDesk newsDesk(LiveMarketView market, Clock clock, BotRunner runner, ScenarioDirector director,
                             HeadlineWriter headlines,
                             @Value("${uncross.news.enabled:true}") boolean enabled,
                             @Value("${uncross.news.min-gap-seconds:60}") long minGap,
                             @Value("${uncross.news.max-gap-seconds:150}") long maxGap) {
        NewsDesk desk = new NewsDesk(market, clock, new Random(), () -> director.status().running(),
                Duration.ofSeconds(minGap), Duration.ofSeconds(maxGap));
        desk.setEnabled(enabled);
        desk.onFlash(flash -> headlines.file(flash.at(), "Simulated news", flash.symbol(), flash.text(), flash.deck(),
                flash.good() ? Headline.Tone.UP : Headline.Tone.DOWN));
        runner.addHook(desk);
        return desk;
    }

    @Bean
    public TournamentAnnouncer tournamentAnnouncer(Tournament tournament, HeadlineWriter headlines, Clock clock) {
        tournament.onFinish(result -> result.leader().ifPresent(winner -> headlines.file(clock.instant(), "Tournament",
                null, winner.name() + " wins the round", "Made " + com.uncross.core.model.Money.format(winner.pnl())
                        + " from the starting whistle; " + result.scores().size() + " bots took part.",
                Headline.Tone.NEUTRAL)));
        return new TournamentAnnouncer();
    }

    /** Marker bean: its job is done when the listener above is registered. */
    public record TournamentAnnouncer() {
    }

    /** The Inspector: watches every event and files cases; suspending cancels the trader's orders. */
    @Bean
    public SurveillanceDesk surveillanceDesk(EventBus bus, Exchange exchange, Suspensions suspensions,
                                             HeadlineWriter headlines) {
        SurveillanceDesk desk = new SurveillanceDesk(SurveillanceDesk.standardRules(), suspensions,
                trader -> exchange.cancelAllFor(trader).join());
        bus.register(desk);
        desk.addListener(headlines);
        return desk;
    }
}
