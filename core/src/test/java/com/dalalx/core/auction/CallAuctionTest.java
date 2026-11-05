package com.dalalx.core.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.dalalx.core.book.OrderBook;
import com.dalalx.core.model.OrderFixtures;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * The textbook example:
 * <pre>
 *   bids 105×100, 104×200, 102×300      asks 100×150, 103×250, 104×100
 *   price  demand  supply  tradable
 *    100    600     150     150
 *    102    600     150     150
 *    103    300     400     300   ← most shares; imbalance 100
 *    104    300     500     300      same shares, imbalance 200
 *    105    100     500     100
 * </pre>
 */
class CallAuctionTest {

    private final OrderFixtures orders = new OrderFixtures();
    private final OrderBook book = new OrderBook(OrderFixtures.SYMBOL);

    private void collect(Side side, long price, long qty) {
        book.addForAuction(orders.limit(side, price, qty));
    }

    private void textbook() {
        collect(Side.BUY, 105, 100);
        collect(Side.BUY, 104, 200);
        collect(Side.BUY, 102, 300);
        collect(Side.SELL, 100, 150);
        collect(Side.SELL, 103, 250);
        collect(Side.SELL, 104, 100);
    }

    @Test
    void picksThePriceThatTradesTheMostShares() {
        textbook();
        assertThat(book.isCrossed()).isTrue();

        assertThat(CallAuction.indicative(book, 101)).contains(new IndicativePrice(103, 300, 300, 400));
    }

    @Test
    void uncrossesEverythingAtOnePriceAndLeavesTheBookUncrossed() {
        textbook();
        List<Trade> trades = CallAuction.uncross(book, 103, new AtomicLong(1000)::incrementAndGet, OrderFixtures.T0);

        assertThat(trades).extracting(Trade::price, Trade::qty)
                .containsExactly(tuple(103L, 100L), tuple(103L, 50L), tuple(103L, 150L));
        assertThat(trades).extracting(Trade::price).containsOnly(103L);
        assertThat(book.isCrossed()).isFalse();
        assertThat(book.bestBid()).hasValue(102);
        assertThat(book.bestAsk()).hasValue(103);
    }

    @Test
    void breaksATieByImbalanceThenByClosenessToTheReference() {
        collect(Side.BUY, 101, 100);
        collect(Side.SELL, 99, 100);
        // At 99 and at 101 the same 100 shares trade with no imbalance: the reference price decides.
        assertThat(CallAuction.indicative(book, 200)).get().extracting(IndicativePrice::price).isEqualTo(101L);
        assertThat(CallAuction.indicative(book, 0)).get().extracting(IndicativePrice::price).isEqualTo(99L);
    }

    @Test
    void nothingTradesWhenBidsAndAsksDoNotOverlap() {
        collect(Side.BUY, 99, 100);
        collect(Side.SELL, 101, 100);
        assertThat(CallAuction.indicative(book, 100)).isEmpty();
    }
}
