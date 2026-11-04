package com.dalalx.core.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import com.dalalx.core.book.OrderBook;
import com.dalalx.core.model.Order;
import com.dalalx.core.model.OrderFixtures;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MarketOrderMatcherTest {

    private final OrderFixtures orders = new OrderFixtures();
    private final OrderBook book = new OrderBook(OrderFixtures.SYMBOL);
    private final Clock clock = Clock.fixed(OrderFixtures.T0, ZoneOffset.UTC);
    private final MatchingStrategy matcher = new MarketOrderMatcher(new AtomicLong()::incrementAndGet, clock);

    private void rest(Side side, long price, long qty) {
        book.add(orders.limit(side, price, qty));
    }

    private MatchResult market(Side side, long qty) {
        return matcher.match(orders.of(OrderType.MARKET, side, 0, qty), book);
    }

    @Test
    void marketOrderOnEmptyBookIsRejectedWithReason() {
        MatchResult result = market(Side.BUY, 10);

        assertThat(result.trades()).isEmpty();
        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.explanation()).hasValue(MarketOrderMatcher.NO_LIQUIDITY);
        assertThat(result.rested()).isFalse();
    }

    @Test
    void marketOrderOnlyLooksAtTheOppositeSide() {
        rest(Side.BUY, 10000, 50); // a bid does not help a market BUY

        MatchResult result = market(Side.BUY, 10);

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(book.bestBid()).hasValue(10000);
    }

    @Test
    void marketBuyWalksTheBookAtEachRestingPrice() {
        rest(Side.SELL, 10200, 50);
        rest(Side.SELL, 10300, 80);
        rest(Side.SELL, 10500, 120);

        MatchResult result = market(Side.BUY, 200);

        assertThat(result.trades()).extracting(Trade::price, Trade::qty)
                .containsExactly(tuple(10200L, 50L), tuple(10300L, 80L), tuple(10500L, 70L));
        assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.explanation()).isEmpty();
        assertThat(book.bestAsk()).hasValue(10500);
    }

    @Test
    void marketSellHitsTheHighestBidsFirst() {
        rest(Side.BUY, 9900, 10);
        rest(Side.BUY, 10000, 10);

        MatchResult result = market(Side.SELL, 15);

        assertThat(result.trades()).extracting(Trade::price, Trade::qty)
                .containsExactly(tuple(10000L, 10L), tuple(9900L, 5L));
        assertThat(result.trades()).allSatisfy(t -> assertThat(t.aggressorSide()).isEqualTo(Side.SELL));
    }

    @Test
    void thinBookFillsWhatItCanAndCancelsTheRest() {
        rest(Side.SELL, 10200, 30);

        MatchResult result = market(Side.BUY, 100);

        assertThat(result.filledQty()).isEqualTo(30);
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.incoming().remainingQty()).isEqualTo(70);
        assertThat(result.explanation()).hasValue(MarketOrderMatcher.BOOK_EXHAUSTED);
        assertThat(result.rested()).isFalse();
        assertThat(book.isEmpty()).isTrue();
    }

    @Test
    void marketOrderNeverRestsInTheBook() {
        rest(Side.SELL, 10200, 5);

        market(Side.BUY, 10);

        assertThat(book.bestBid()).isEmpty();
        assertThat(book.orderCount()).isZero();
    }

    @Test
    void refusesOtherOrderTypes() {
        Order limit = orders.limit(Side.BUY, 10000, 10);

        assertThatIllegalArgumentException().isThrownBy(() -> matcher.match(limit, book))
                .withMessageContaining("MARKET");
    }
}
