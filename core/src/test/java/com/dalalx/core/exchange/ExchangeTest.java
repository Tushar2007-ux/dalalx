package com.dalalx.core.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dalalx.core.events.EventBus;
import com.dalalx.core.matching.CancelResult;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExchangeTest {

    private final Exchange exchange = new Exchange(Exchange.DEFAULT_STOCKS, new EventBus(), Clock.systemUTC());

    @BeforeEach
    void start() {
        exchange.start();
    }

    @AfterEach
    void stop() throws InterruptedException {
        exchange.shutdown(5, TimeUnit.SECONDS);
    }

    private MatchResult submit(String symbol, Side side, long price, long qty) {
        return exchange.submit(new OrderRequest(symbol, "T", side, OrderType.LIMIT, price, qty)).join();
    }

    @Test
    void listsTheFiveSimulatedStocks() {
        assertThat(exchange.stocks()).extracting(ListedStock::symbol)
                .containsExactly("RELIANCE", "TCS", "INFY", "HDFCBANK", "TATAMOTORS");
    }

    @Test
    void ordersAreRoutedToTheirOwnSymbolsBook() {
        submit("TCS", Side.BUY, 409000, 10);
        submit("INFY", Side.SELL, 154500, 5);

        assertThat(exchange.snapshot("TCS", 5).join().bids()).hasSize(1);
        assertThat(exchange.snapshot("TCS", 5).join().asks()).isEmpty();
        assertThat(exchange.snapshot("INFY", 5).join().asks()).hasSize(1);
    }

    @Test
    void crossingOrdersTradeThroughTheWorker() {
        submit("RELIANCE", Side.SELL, 246000, 10);

        MatchResult buy = submit("RELIANCE", Side.BUY, 246000, 10);

        assertThat(buy.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(buy.trades()).singleElement().satisfies(t -> assertThat(t.price()).isEqualTo(246000));
    }

    @Test
    void unknownSymbolIsRefused() {
        OrderRequest request = new OrderRequest("ADANI", "T", Side.BUY, OrderType.LIMIT, 100, 1);

        assertThatIllegalArgumentException().isThrownBy(() -> exchange.submit(request)).withMessageContaining("ADANI");
    }

    @Test
    void cancelNeedsOnlyTheOrderId() {
        long id = submit("HDFCBANK", Side.BUY, 168000, 10).incoming().id();

        CancelResult result = exchange.cancel(id).join();

        assertThat(result.cancelled()).isTrue();
        assertThat(exchange.snapshot("HDFCBANK", 5).join().bids()).isEmpty();
    }

    @Test
    void cancellingAnIdTheExchangeNeverIssuedFails() {
        assertThat(exchange.cancel(987654).join().cancelled()).isFalse();
    }

    @Test
    void anExceptionInsideACommandReachesTheCallerAndTheWorkerSurvives() {
        CompletableFuture<Object> boom = exchange.onWorker("TCS", "boom", engine -> {
            throw new IllegalStateException("boom");
        });

        assertThatThrownBy(boom::get).isInstanceOf(ExecutionException.class).hasRootCauseMessage("boom");
        assertThat(submit("TCS", Side.BUY, 409000, 1).status()).isEqualTo(OrderStatus.NEW); // still working
    }

    @Test
    void twoSymbolsReallyRunInParallel() throws Exception {
        // RELIANCE's worker waits until TCS's worker has run a command. If the two
        // symbols shared one thread, RELIANCE would wait forever and this would time out.
        CountDownLatch tcsRan = new CountDownLatch(1);
        CompletableFuture<Boolean> reliance = exchange.onWorker("RELIANCE", "wait", engine -> {
            try {
                return tcsRan.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        exchange.onWorker("TCS", "signal", engine -> {
            tcsRan.countDown();
            return null;
        });

        assertThat(reliance.get(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void eachSymbolHasItsOwnNamedWorkerThread() {
        String relianceThread = exchange.onWorker("RELIANCE", "who", e -> Thread.currentThread().getName()).join();
        String tcsThread = exchange.onWorker("TCS", "who", e -> Thread.currentThread().getName()).join();

        assertThat(relianceThread).isEqualTo("worker-RELIANCE");
        assertThat(tcsThread).isEqualTo("worker-TCS");
    }

    @Test
    void afterShutdownNewCommandsAreRefusedButQueuedOnesFinished() throws Exception {
        CompletableFuture<MatchResult> queued = exchange.submit(
                new OrderRequest("TCS", "T", Side.BUY, OrderType.LIMIT, 409000, 1));

        assertThat(exchange.shutdown(5, TimeUnit.SECONDS)).isTrue();

        assertThat(queued).isCompleted();
        CompletableFuture<MatchResult> late = exchange.submit(
                new OrderRequest("TCS", "T", Side.BUY, OrderType.LIMIT, 409000, 1));
        assertThatThrownBy(late::get).hasCauseInstanceOf(RejectedExecutionException.class);
    }
}
