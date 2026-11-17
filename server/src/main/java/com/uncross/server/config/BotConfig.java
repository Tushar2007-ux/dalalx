package com.uncross.server.config;

import com.uncross.core.bots.BotLedger;
import com.uncross.core.bots.BotRunner;
import com.uncross.core.bots.ExchangeGateway;
import com.uncross.core.bots.LiveMarketView;
import com.uncross.core.bots.StandardBots;
import com.uncross.core.bots.Tournament;
import java.time.Clock;
import com.uncross.core.events.EventBus;
import com.uncross.core.exchange.Exchange;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the simulated traders from core into the running server.
 *
 * <p>All logic lives in {@code com.uncross.core.bots}; this class only creates
 * the objects, registers the observers on the event bus, and starts the bot
 * thread if {@code uncross.bots.enabled} is true (tests switch it off so they
 * see a quiet market).
 */
@Configuration
public class BotConfig {

    @Bean
    public LiveMarketView liveMarketView(Exchange exchange, EventBus bus) {
        LiveMarketView view = new LiveMarketView(exchange.stocks());
        bus.register(view);
        exchange.onListing(view::listed); // a stock listed later is traded by the bots too
        return view;
    }

    @Bean
    public BotLedger botLedger(LiveMarketView market, EventBus bus) {
        BotLedger ledger = new BotLedger(market);
        bus.register(ledger);
        return ledger;
    }

    /**
     * Depends on {@code ExchangeShutdown} on purpose: Spring destroys a bean
     * before the beans it depends on, so the bots stop (and withdraw their
     * quotes) while the exchange's workers are still running.
     */
    @Bean(destroyMethod = "stop")
    public BotRunner botRunner(Exchange exchange, LiveMarketView market,
                               ExchangeConfig.ExchangeShutdown exchangeShutdown,
                               @Value("${uncross.bots.enabled:true}") boolean enabled,
                               @Value("${uncross.bots.tick-ms:250}") long tickMillis) {
        BotRunner runner = new BotRunner(StandardBots.create(), market, new ExchangeGateway(exchange),
                new Random(), tickMillis);
        // Started by JournalKeeper once the day's journal has been replayed, so no bot trades mid-replay.
        return runner;
    }

    @Bean
    public Tournament tournament(BotRunner runner, BotLedger ledger, Clock clock) {
        Tournament tournament = new Tournament(runner, id -> ledger.stats(id).pnl(), clock);
        runner.addHook(tournament);
        return tournament;
    }
}
