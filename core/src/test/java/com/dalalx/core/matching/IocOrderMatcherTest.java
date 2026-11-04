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

class IocOrderMatcherTest {

    private final OrderFixtures orders = new OrderFixtures();
    private final OrderBook book = new OrderBook(OrderFixtures.SYMBOL);
    private final Clock clock = Clock.fixed(OrderFixtures.T0, ZoneOffset.UTC);
    private final MatchingStrategy matcher = new IocOrderMatcher(new AtomicLong()::incrementAndGet, clock);

    private void rest(Side side, long price, long qty) {
        book.add(orders.limit(side, price, qty));
    }

    private MatchResult ioc(Side side, long price, long qty) {
        return matcher.match(orders.of(OrderType.IOC, side, price, qty), book);
    }

    @Test
    void iocOnEmptyBookIsCancelledAndLeavesNothingBehind() {
        MatchResult result = ioc(Side.BUY, 10000, 10);

        assertThat(result.trades()).isEmpty();
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.explanation()).hasValue(IocOrderMatcher.NOT_IMMEDIATELY_FILLABLE);
        assertThat(book.isEmpty()).isTrue();
    }

    @Test
    void iocThatDoesNotCrossIsCancelledAndBookIsUnchanged() {
        rest(Side.SELL, 10200, 10);

        MatchResult result = ioc(Side.BUY, 10100, 10);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(book.bestAsk()).hasValue(10200);
        assertThat(book.bestBid()).isEmpty();
    }

    @Test
    void iocFillsUpToItsLimitThenCancelsTheRemainder() {
        // The worked example from the IocOrderMatcher Javadoc.
        rest(Side.SELL, 10200, 50);
        rest(Side.SELL, 10500, 80);

        MatchResult result = ioc(Side.BUY, 10300, 100);

        assertThat(result.trades()).extracting(Trade::price, Trade::qty).containsExactly(tuple(10200L, 50L));
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.incoming().filledQty()).isEqualTo(50);
        assertThat(result.rested()).isFalse();
        assertThat(book.bestBid()).isEmpty();          // nothing rested at ₹103
        assertThat(book.bestAsk()).hasValue(10500);    // ₹105 untouched: above the limit
    }

    @Test
    void iocThatFullyFillsIsFilledWithNoReason() {
        rest(Side.BUY, 10000, 40);
        rest(Side.BUY, 9950, 40);

        MatchResult result = ioc(Side.SELL, 9950, 60);

        assertThat(result.trades()).extracting(Trade::price, Trade::qty)
                .containsExactly(tuple(10000L, 40L), tuple(9950L, 20L));
        assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.explanation()).isEmpty();
    }

    @Test
    void sameOrderAsLimitWouldRestButAsIocDoesNot() {
        rest(Side.SELL, 10200, 50);
        MatchingStrategy limitMatcher = new LimitOrderMatcher(new AtomicLong(100)::incrementAndGet, clock);
        OrderBook other = new OrderBook(OrderFixtures.SYMBOL);
        other.add(orders.limit(Side.SELL, 10200, 50));

        MatchResult asIoc = ioc(Side.BUY, 10300, 100);
        MatchResult asLimit = limitMatcher.match(orders.limit(Side.BUY, 10300, 100), other);

        assertThat(asIoc.filledQty()).isEqualTo(asLimit.filledQty()).isEqualTo(50);
        assertThat(asIoc.rested()).isFalse();
        assertThat(asLimit.rested()).isTrue();
        assertThat(other.bestBid()).hasValue(10300);
    }

    @Test
    void refusesOtherOrderTypes() {
        Order limit = orders.limit(Side.BUY, 10000, 10);

        assertThatIllegalArgumentException().isThrownBy(() -> matcher.match(limit, book))
                .withMessageContaining("IOC");
    }
}
