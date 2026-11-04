package com.dalalx.server.config;

import com.dalalx.core.bots.BotRunner;
import com.dalalx.core.events.EventBus;
import com.dalalx.core.exchange.Exchange;
import com.dalalx.core.exchange.ListedStock;
import com.dalalx.core.headlines.HeadlineWriter;
import com.dalalx.core.scenarios.ScenarioDirector;
import com.dalalx.core.scenarios.Storylines;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the storylines and the newsroom into the server. All behaviour lives
 * in core ({@code scenarios}, {@code headlines}); this class only connects the
 * observers: the director runs on the bot thread as a tick hook, and the
 * headline writer listens to both the engine and the director.
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
    public HeadlineWriter headlineWriter(Exchange exchange, BotRunner runner, EventBus bus, ScenarioDirector director) {
        HeadlineWriter writer = new HeadlineWriter(
                symbol -> exchange.stock(symbol).map(ListedStock::referencePrice).orElseThrow(),
                id -> "YOU".equals(id) ? "A reader" : runner.nameOf(id));
        bus.register(writer);
        director.addListener(writer);
        return writer;
    }
}
