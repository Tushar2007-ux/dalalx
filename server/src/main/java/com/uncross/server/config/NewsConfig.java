package com.uncross.server.config;

import com.uncross.core.bots.BotRunner;
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
    public HeadlineWriter headlineWriter(Exchange exchange, BotRunner runner, EventBus bus, ScenarioDirector director) {
        HeadlineWriter writer = new HeadlineWriter(
                symbol -> exchange.stock(symbol).map(ListedStock::referencePrice).orElseThrow(),
                id -> "YOU".equals(id) ? "A reader" : runner.nameOf(id));
        bus.register(writer);
        director.addListener(writer);
        return writer;
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
