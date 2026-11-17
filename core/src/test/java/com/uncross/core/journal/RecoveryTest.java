package com.uncross.core.journal;

import static org.assertj.core.api.Assertions.assertThat;

import com.uncross.core.book.BookSnapshot;
import com.uncross.core.breaker.BreakerRule;
import com.uncross.core.events.EventBus;
import com.uncross.core.events.TradeExecuted;
import com.uncross.core.exchange.Exchange;
import com.uncross.core.exchange.ListedStock;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import com.uncross.core.model.Trade;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The promise of the journal: replay a day's commands into a fresh exchange
 * and you get the same books and the same trades.
 */
class RecoveryTest {

    private static final List<ListedStock> STOCKS = List.of(
            new ListedStock("TCS", "TCS", 400_000), new ListedStock("INFY", "INFY", 150_000));

    private final List<Exchange> started = new ArrayList<>();

    @AfterEach
    void stop() throws InterruptedException {
        for (Exchange e : started) {
            e.shutdown(2, TimeUnit.SECONDS);
        }
    }

    private Exchange exchange(EventBus bus, Clock clock) {
        Exchange exchange = new Exchange(STOCKS, bus, clock, BreakerRule.STANDARD);
        exchange.start();
        started.add(exchange);
        return exchange;
    }

    private static OrderRequest limit(String symbol, String who, Side side, long price, long qty) {
        return new OrderRequest(symbol, who, side, OrderType.LIMIT, price, qty);
    }

    @Test
    void replayingTheJournalRebuildsTheSameBooksAndTrades() {
        List<JournalEntry> journal = Collections.synchronizedList(new ArrayList<>());
        List<Trade> originalTrades = Collections.synchronizedList(new ArrayList<>());
        EventBus bus = new EventBus();
        bus.register(e -> { if (e instanceof TradeExecuted t) originalTrades.add(t.trade()); });
        Exchange original = exchange(bus, Clock.systemUTC());
        original.journalTo(journal::add);

        original.submit(limit("TCS", "A", Side.SELL, 401_000, 100)).join();
        long cancelMe = original.submit(limit("TCS", "A", Side.SELL, 402_000, 100)).join().incoming().id();
        long amendMe = original.submit(limit("TCS", "B", Side.BUY, 399_000, 100)).join().incoming().id();
        original.submit(limit("INFY", "C", Side.BUY, 150_000, 300)).join();
        original.submit(new OrderRequest("INFY", "D", Side.SELL, OrderType.MARKET, 0, 120)).join();
        original.cancel(cancelMe).join();
        original.amend(amendMe, 401_000, 60).join(); // now crosses: trades 60 at 401,000
        original.list(new ListedStock("WIPRO", "Wipro", 25_000));
        original.submit(limit("WIPRO", "E", Side.BUY, 25_000, 10)).join();
        original.submit(new OrderRequest("TCS", "F", Side.SELL, OrderType.STOP_LOSS, 390_000, 20)).join();
        original.submit(new OrderRequest("TCS", "G", Side.SELL, OrderType.ICEBERG, 403_000, 500, 50)).join();

        List<Trade> replayedTrades = Collections.synchronizedList(new ArrayList<>());
        EventBus freshBus = new EventBus();
        freshBus.register(e -> { if (e instanceof TradeExecuted t) replayedTrades.add(t.trade()); });
        ReplayClock clock = new ReplayClock(Clock.systemUTC());
        Exchange fresh = exchange(freshBus, clock);

        Recovery.Report report = Recovery.replay(List.copyOf(journal), fresh, clock, opened -> { });

        assertThat(report.entries()).isEqualTo(journal.size());
        assertThat(clock.isPinned()).isFalse();
        for (String symbol : List.of("TCS", "INFY", "WIPRO")) {
            BookSnapshot was = original.snapshot(symbol, 10).join();
            BookSnapshot now = fresh.snapshot(symbol, 10).join();
            assertThat(now).as(symbol).isEqualTo(was);
        }
        assertThat(replayedTrades).extracting(Trade::symbol, Trade::price, Trade::qty)
                .containsExactlyElementsOf(originalTrades.stream()
                        .map(t -> org.assertj.core.groups.Tuple.tuple(t.symbol(), t.price(), t.qty())).toList());
        // Each replayed trade is stamped with its order's journalled arrival time: the original
        // trade happened a few microseconds after its order arrived, so they agree to within a millisecond.
        for (int i = 0; i < originalTrades.size(); i++) {
            assertThat(replayedTrades.get(i).timestamp())
                    .isCloseTo(originalTrades.get(i).timestamp(), org.assertj.core.api.Assertions.within(5, ChronoUnit.MILLIS));
        }
    }

    @Test
    void aRejectedOrderIsNotWrittenDown() {
        List<JournalEntry> journal = Collections.synchronizedList(new ArrayList<>());
        Exchange exchange = exchange(new EventBus(), Clock.systemUTC());
        exchange.journalTo(journal::add);

        exchange.submit(new OrderRequest("TCS", "A", Side.BUY, OrderType.MARKET, 0, 10)).join(); // empty book: rejected

        assertThat(journal).isEmpty();
    }
}
