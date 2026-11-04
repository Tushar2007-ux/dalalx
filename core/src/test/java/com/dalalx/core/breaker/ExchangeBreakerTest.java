package com.dalalx.core.breaker;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.events.EventBus;
import com.dalalx.core.exchange.Exchange;
import com.dalalx.core.exchange.ListedStock;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The exchange clock ends a halt on time even when nobody sends an order. */
class ExchangeBreakerTest {

    private final EventBus bus = new EventBus();
    private final StatusBoard board = new StatusBoard();
    private final Exchange exchange = new Exchange(List.of(new ListedStock("TCS", "TCS", 100_000)), bus,
            Clock.systemUTC(), new BreakerRule(1_000, Duration.ofSeconds(60), Duration.ofMillis(400)));

    @AfterEach
    void stop() throws InterruptedException {
        exchange.shutdown(5, TimeUnit.SECONDS);
    }

    private void send(String trader, Side side, OrderType type, long price, long qty) {
        exchange.submit(new OrderRequest("TCS", trader, side, type, price, qty)).join();
    }

    @Test
    void haltsThenReopensByItself() throws InterruptedException {
        bus.register(board);
        exchange.start();
        send("A", Side.BUY, OrderType.LIMIT, 100_000, 10);
        send("A", Side.BUY, OrderType.LIMIT, 89_000, 10);
        send("B", Side.SELL, OrderType.MARKET, 0, 20);

        assertThat(board.status("TCS").phase()).isEqualTo(MarketPhase.HALTED);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (board.status("TCS").phase() != MarketPhase.OPEN && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(board.status("TCS").phase()).isEqualTo(MarketPhase.OPEN);
    }
}
