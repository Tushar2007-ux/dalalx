package com.uncross.core.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.uncross.core.events.EventBus;
import com.uncross.core.journal.JournalEntry;
import com.uncross.core.matching.AmendResult;
import com.uncross.core.matching.MatchResult;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Listing a stock while the market runs, and amending through the exchange. */
class ListingTest {

    private final List<JournalEntry> journal = new ArrayList<>();
    private final List<ListedStock> heard = new ArrayList<>();
    private final Exchange exchange = new Exchange(List.of(new ListedStock("TCS", "TCS", 400_000)),
            new EventBus(), Clock.systemUTC());

    ListingTest() {
        exchange.start();
        exchange.journalTo(journal::add);
        exchange.onListing(heard::add);
    }

    @AfterEach
    void stop() throws InterruptedException {
        exchange.shutdown(2, TimeUnit.SECONDS);
    }

    @Test
    void aNewStockGetsItsOwnWorkerAndTradesAtOnce() {
        exchange.list(new ListedStock("WIPRO", "Wipro (simulated)", 25_000));

        exchange.submit(new OrderRequest("WIPRO", "A", Side.SELL, OrderType.LIMIT, 25_000, 10)).join();
        MatchResult buy = exchange.submit(new OrderRequest("WIPRO", "B", Side.BUY, OrderType.MARKET, 0, 10)).join();

        assertThat(buy.filledQty()).isEqualTo(10);
        assertThat(exchange.stocks()).extracting(ListedStock::symbol).containsExactly("TCS", "WIPRO");
        assertThat(exchange.workerStats()).extracting(WorkerStats::thread).contains("worker-WIPRO");
        assertThat(heard).extracting(ListedStock::symbol).containsExactly("WIPRO");
        assertThat(journal).first().isInstanceOf(JournalEntry.Listed.class);
    }

    @Test
    void aSymbolMustBeWellFormedAndNew() {
        assertThatIllegalArgumentException().isThrownBy(() -> exchange.list(new ListedStock("TCS", "again", 1)))
                .withMessageContaining("already listed");
        assertThatIllegalArgumentException().isThrownBy(() -> exchange.list(new ListedStock("wipro", "lower", 1)));
        assertThatIllegalArgumentException().isThrownBy(() -> exchange.list(new ListedStock("../X", "path", 1)));
    }

    @Test
    void theExchangeHasRoomForTwelve() {
        for (int i = 1; i < Exchange.MAX_STOCKS; i++) {
            exchange.list(new ListedStock("S" + (char) ('A' + i), "Stock " + i, 10_000));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> exchange.list(new ListedStock("ZZ", "one too many", 1)))
                .withMessageContaining("full");
    }

    @Test
    void anAmendIsRoutedToTheRightStockAndJournalled() {
        long id = exchange.submit(new OrderRequest("TCS", "A", Side.BUY, OrderType.LIMIT, 399_000, 100))
                .join().incoming().id();

        AmendResult result = exchange.amend(id, 398_000, 100).join();
        long replacement = result.order().id();

        assertThat(result.amended()).isTrue();
        assertThat(exchange.cancel(replacement).join().cancelled()).isTrue(); // the new id is routable too
        assertThat(journal).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("Submitted", "Amended", "Cancelled");
        assertThat(exchange.amend(12_345, 1, 1).join().amended()).isFalse();
    }
}
