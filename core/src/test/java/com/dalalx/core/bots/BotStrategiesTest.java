package com.dalalx.core.bots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.book.LevelSnapshot;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class BotStrategiesTest {

    private final EngineGateway gateway = new EngineGateway();
    private final RandomGenerator dice = new ZeroRandom();

    private BotContext context(FakeMarket market) {
        return new BotContext(market, gateway, dice);
    }

    @Test
    void marketMakerQuotesFiveLevelsEachSideAroundAFairPrice() {
        FakeMarket market = new FakeMarket().with("TCS", 400000, 400000);
        new MarketMakerBot().act(context(market));

        BookSnapshot book = gateway.engine("TCS").snapshot(10);
        assertThat(book.bids()).extracting(LevelSnapshot::price).containsExactly(399800L, 399640L, 399480L, 399320L, 399160L);
        assertThat(book.asks()).extracting(LevelSnapshot::price).containsExactly(400200L, 400360L, 400520L, 400680L, 400840L);
        assertThat(book.bids()).allSatisfy(l -> assertThat(l.totalQty()).isEqualTo(MarketMakerBot.MIN_QTY));
    }

    @Test
    void marketMakerReplacesItsOldQuotesInsteadOfPilingUp() {
        FakeMarket market = new FakeMarket().with("TCS", 400000, 400000);
        MarketMakerBot munim = new MarketMakerBot();
        munim.act(context(market));
        munim.act(context(market));

        assertThat(gateway.cancelled).hasSize(10);
        assertThat(gateway.engine("TCS").snapshot(10).bids()).hasSize(5);
        assertThat(munim.restingCount()).isEqualTo(10);
    }

    @Test
    void marketMakerLeansBackTowardTheReferencePrice() {
        FakeMarket market = new FakeMarket().with("TCS", 400000, 440000); // 10% above reference
        assertThat(MarketMakerBot.fairPrice(market, "TCS")).isEqualTo(439600); // 1/100th of the way back
    }

    @Test
    void momentumBuysARisingStockAndSellsAFallingOne() {
        new MomentumBot().act(context(new FakeMarket().with("INFY", 150000, 150000, 150100, 150200, 150300, 150400)));
        new MomentumBot().act(context(new FakeMarket().with("INFY", 150000, 150400, 150300, 150200, 150100, 150000)));

        assertThat(gateway.sent).extracting(OrderRequest::side, OrderRequest::type, OrderRequest::price)
                .containsExactly(tuple(Side.BUY, OrderType.IOC, 150580L), tuple(Side.SELL, OrderType.IOC, 149820L));
    }

    @Test
    void panicSellerDumpsAFallingStockFarBelowTheLastPrice() {
        new PanicSellerBot().act(context(new FakeMarket().with("TCS", 400000, 400000, 399800, 399500, 399000, 398500, 398000, 397500, 397000)));

        OrderRequest dump = gateway.sent.get(0);
        assertThat(dump.side()).isEqualTo(Side.SELL);
        assertThat(dump.price()).isEqualTo(Ticks.moveBy(397000, -PanicSellerBot.DUMP_BP));
        assertThat(dump.qty()).isEqualTo(200);
    }

    @Test
    void whaleWorksABlockInSlicesUntilItIsDone() {
        FakeMarket market = new FakeMarket().with("HDFCBANK", 168300, 168300);
        // Plenty of bids for the whale (who sells, since ZeroRandom's nextBoolean is false) to hit.
        gateway.submit(new OrderRequest("HDFCBANK", "MUNIM", Side.BUY, OrderType.LIMIT, 168300, 10_000));
        WhaleBot whale = new WhaleBot();

        whale.act(context(market)); // decides
        assertThat(whale.parent()).isEqualTo(new WhaleBot.Parent("HDFCBANK", Side.SELL, 2_000, WhaleBot.MAX_SLICES));
        for (int i = 0; i < 20; i++) {
            whale.act(context(market));
        }

        assertThat(whale.parent()).isNull(); // 2,000 shares in 20 slices of 100
        assertThat(gateway.sent.subList(1, gateway.sent.size())).hasSize(20)
                .allSatisfy(r -> assertThat(r.qty()).isEqualTo(100));
    }
}
