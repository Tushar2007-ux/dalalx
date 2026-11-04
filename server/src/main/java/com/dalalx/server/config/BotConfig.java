package com.dalalx.server.config;

import com.dalalx.core.bots.BotLedger;
import com.dalalx.core.bots.BotRunner;
import com.dalalx.core.bots.ExchangeGateway;
import com.dalalx.core.bots.LiveMarketView;
import com.dalalx.core.bots.StandardBots;
import com.dalalx.core.events.EventBus;
import com.dalalx.core.exchange.Exchange;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the simulated traders from core into the running server.
 *
 * <p>All logic lives in {@code com.dalalx.core.bots}; this class only creates
 * the objects, registers the observers on the event bus, and starts the bot
 * thread if {@code dalalx.bots.enabled} is true (tests switch it off so they
 * see a quiet market).
 */
@Configuration
public class BotConfig {

    @Bean
    public LiveMarketView liveMarketView(Exchange exchange, EventBus bus) {
        LiveMarketView view = new LiveMarketView(exchange.stocks());
        bus.register(view);
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
                               @Value("${dalalx.bots.enabled:true}") boolean enabled,
                               @Value("${dalalx.bots.tick-ms:250}") long tickMillis) {
        BotRunner runner = new BotRunner(StandardBots.create(), market, new ExchangeGateway(exchange),
                new Random(), tickMillis);
        if (enabled) {
            runner.start();
        }
        return runner;
    }
}
