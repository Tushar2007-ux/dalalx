package com.uncross.core.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.uncross.core.exchange.ListedStock;
import com.uncross.core.journal.JournalEntry.Submitted;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Playing a saved day back at speed, into a private exchange. */
class RewindTest {

    private static final Instant T = Instant.parse("2026-11-17T09:15:00Z");
    private static final List<ListedStock> STOCKS = List.of(new ListedStock("TCS", "TCS", 400_000));

    private static Submitted order(int secondsIn, long id, Side side, OrderType type, long price, long qty) {
        return new Submitted(T.plusSeconds(secondsIn), id, new OrderRequest("TCS", "T" + id, side, type, price, qty));
    }

    @Test
    void theTapePlaysToTheEndAndShowsTheDaysPrices() throws Exception {
        List<JournalEntry> day = List.of(
                order(0, 1, Side.SELL, OrderType.LIMIT, 401_000, 100),
                order(30, 2, Side.SELL, OrderType.LIMIT, 402_000, 100),
                order(60, 3, Side.BUY, OrderType.MARKET, 0, 150));

        try (Rewind rewind = new Rewind("today", day, STOCKS, Rewind.MAX_SPEED).start()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (rewind.state() != Rewind.State.FINISHED && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            Rewind.View view = rewind.view();

            assertThat(view.state()).isEqualTo(Rewind.State.FINISHED);
            assertThat(view.done()).isEqualTo(3);
            assertThat(view.at()).isEqualTo(T.plusSeconds(60));
            assertThat(view.stocks()).singleElement().satisfies(s -> {
                assertThat(s.open()).isEqualTo(401_000);
                assertThat(s.last()).isEqualTo(402_000);
                assertThat(s.volume()).isEqualTo(150);
            });
            assertThat(view.tape()).hasSize(2);
            assertThat(view.tape().get(0).at()).isEqualTo(T.plusSeconds(60)); // trades keep their original time
        }
    }

    @Test
    void itCanBePausedAndRefusesSillySpeeds() throws Exception {
        try (Rewind rewind = new Rewind("today", List.of(order(0, 1, Side.SELL, OrderType.LIMIT, 1, 1)), STOCKS, 1)) {
            rewind.pause();
            assertThat(rewind.state()).isEqualTo(Rewind.State.PAUSED);
            rewind.resume();
            assertThat(rewind.state()).isEqualTo(Rewind.State.PLAYING);
            assertThatIllegalArgumentException().isThrownBy(() -> rewind.setSpeed(0));
            assertThatIllegalArgumentException().isThrownBy(() -> rewind.setSpeed(Rewind.MAX_SPEED + 1));
        }
    }
}
