package com.uncross.server.config;

import com.uncross.core.accounts.Accounts;
import com.uncross.core.accounts.RiskDesk;
import com.uncross.core.audience.Guest;
import com.uncross.core.audience.GuestDesk;
import com.uncross.core.bots.BotRunner;
import com.uncross.core.breaker.StatusBoard;
import com.uncross.core.events.EventBus;
import com.uncross.core.exchange.CommandSource;
import com.uncross.core.exchange.Exchange;
import com.uncross.core.exchange.ListedStock;
import com.uncross.core.journal.ReplayClock;
import com.uncross.core.marketdata.DayTape;
import com.uncross.core.matching.OrderGate;
import com.uncross.core.marketdata.CandleAggregator;
import com.uncross.core.marketdata.TradeHistory;
import com.uncross.core.surveillance.Suspensions;
import com.uncross.core.xray.XRayDesk;
import com.uncross.server.api.OrderController;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the core objects as Spring beans, so controllers can have them injected.
 *
 * <p>This is the only place the server <em>constructs</em> core classes. The
 * core itself has no Spring annotations: Spring calls these plain constructors
 * and manages the lifecycle (start on boot, shut down on exit). That keeps the
 * rule that the dependency points only one way, {@code server → core}.
 *
 * <p>The market-data listeners are registered on the {@link EventBus}
 * <em>before</em> the exchange starts, so they never miss a trade.
 */
@Configuration
public class ExchangeConfig {

    @Bean
    public ReplayClock clock() {
        // The real time, except while the journal is being replayed at start-up (see JournalKeeper).
        return new ReplayClock(Clock.systemUTC());
    }

    @Bean
    public Accounts accounts(Clock clock) {
        return new Accounts(clock);
    }

    @Bean
    public RiskDesk riskDesk(Accounts accounts, EventBus bus, ObjectProvider<Exchange> exchange) {
        // The exchange is looked up lazily: it needs this desk as its gate, so it cannot exist yet.
        RiskDesk desk = new RiskDesk(accounts, symbol -> exchange.getObject().stock(symbol)
                .map(ListedStock::referencePrice).orElseThrow(() -> new IllegalArgumentException("unknown " + symbol)));
        bus.register(desk);
        return desk;
    }

    @Bean
    public DayTape dayTape(EventBus bus) {
        DayTape tape = new DayTape();
        bus.register(tape);
        return tape;
    }

    @Bean
    public EventBus eventBus() {
        return new EventBus();
    }

    @Bean
    public TradeHistory tradeHistory(EventBus bus) {
        TradeHistory history = new TradeHistory();
        bus.register(history);
        return history;
    }

    @Bean
    public CandleAggregator candleAggregator(EventBus bus) {
        CandleAggregator candles = new CandleAggregator();
        bus.register(candles);
        return candles;
    }

    /**
     * The exchange, started as soon as it's created. Spring calls
     * the {@link ExchangeShutdown} bean ({@link ExchangeShutdown}) on shutdown, so
     * queued orders finish and the worker threads exit cleanly.
     */
    @Bean
    public StatusBoard statusBoard(EventBus bus) {
        StatusBoard board = new StatusBoard();
        bus.register(board);
        return board;
    }

    /**
     * X-Rays the human trader's orders. Bot names are looked up lazily (the
     * bots are created after the exchange), so the narration can say
     * "The Munim's order..." instead of "MUNIM's order...".
     */
    @Bean
    public XRayDesk xrayDesk(ObjectProvider<BotRunner> runner, ObjectProvider<GuestDesk> guests) {
        return new XRayDesk(id -> OrderController.HUMAN.equals(id) || Accounts.isAccount(id), id -> {
            if (OrderController.HUMAN.equals(id)) {
                return "You";
            }
            if (Accounts.isAccount(id)) {
                return "You";
            }
            if (Guest.isGuest(id) && guests.getIfAvailable() != null) {
                return guests.getObject().byTrader(id).map(Guest::name).orElse(id);
            }
            return runner.getIfAvailable() == null ? id : runner.getObject().nameOf(id);
        });
    }

    /** Traders barred by surveillance; every engine checks it before accepting an order. */
    @Bean
    public Suspensions suspensions() {
        return new Suspensions();
    }

    @Bean(destroyMethod = "")
    public Exchange exchange(EventBus bus, Clock clock, TradeHistory history, CandleAggregator candles, XRayDesk xrays,
                             Suspensions suspensions, RiskDesk riskDesk) {
        Exchange exchange = new Exchange(Exchange.DEFAULT_STOCKS, bus, clock);
        exchange.recordXRays(xrays);
        // Suspended traders are turned away first; the risk desk comes last because it sets money aside.
        exchange.guardWith(OrderGate.allOf(request -> suspensions.refusal(request.traderId()), riskDesk));
        // Labels each order's source for the Engine Room (monitoring only).
        exchange.classifySourcesWith(trader -> OrderController.HUMAN.equals(trader) ? CommandSource.PRESENTER
                : Guest.isGuest(trader) || Accounts.isAccount(trader) ? CommandSource.AUDIENCE : CommandSource.BOT);
        exchange.start();
        return exchange;
    }

    @Bean
    public ExchangeShutdown exchangeShutdown(Exchange exchange) {
        return new ExchangeShutdown(exchange);
    }

    /** Stops the exchange's worker threads when Spring shuts down. */
    public record ExchangeShutdown(Exchange exchange) implements org.springframework.beans.factory.DisposableBean {
        @Override
        public void destroy() throws InterruptedException {
            exchange.shutdown(5, TimeUnit.SECONDS);
        }
    }
}
