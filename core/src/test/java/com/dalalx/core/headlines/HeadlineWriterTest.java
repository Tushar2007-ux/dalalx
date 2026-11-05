package com.dalalx.core.headlines;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.breaker.MarketPhase;
import com.dalalx.core.events.AuctionUpdate;
import com.dalalx.core.events.MarketStatusChanged;
import com.dalalx.core.headlines.Headline.Tone;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeadlineWriterTest {

    private static final Instant T0 = Instant.parse("2026-11-04T09:15:00Z");
    private final Map<String, String> bylines = Map.of("WHALE", "The Whale", "PANIC", "Panic Seller");
    private final HeadlineWriter writer = new HeadlineWriter(s -> 100_000, id -> bylines.getOrDefault(id, id));
    private int seconds;

    private void trade(long price, long qty, Side aggressor) {
        writer.onTrade(new Trade(1, "TCS", price, qty, 1, 2, "WHALE", "PANIC", aggressor, T0.plusSeconds(seconds++)));
    }

    private List<String> texts() {
        return writer.latest(50).stream().map(Headline::text).toList().reversed();
    }

    @Test
    void reportsEachWholePercentMoveOnce() {
        trade(100_500, 10, Side.BUY);   // +0.5%: no news
        trade(101_000, 10, Side.BUY);   // +1%
        trade(101_500, 10, Side.BUY);   // still 1%: no repeat
        trade(102_300, 10, Side.BUY);   // +2%

        assertThat(texts()).containsExactly("TCS climbs 1% to ₹1,010.00", "TCS climbs 2% to ₹1,023.00");
        assertThat(writer.latest(1).get(0).tone()).isEqualTo(Tone.UP);
    }

    @Test
    void reportsNewLowsAndOnlyBigRecoveries() {
        trade(97_000, 10, Side.SELL);   // −3%
        trade(98_000, 10, Side.BUY);    // −2%: only one level back, no news
        trade(99_500, 10, Side.BUY);    // −0.5%: three levels back
        trade(98_800, 10, Side.SELL);   // −1.2%: a level it has already seen, no news

        assertThat(texts()).containsExactly(
                "TCS slides 3% to ₹970.00",
                "TCS claws back losses, back within 1% of its reference at ₹995.00");
    }

    @Test
    void aPriceWobblingAroundOneLineIsNotNewsTwice() {
        trade(101_000, 10, Side.BUY);   // +1%
        trade(100_900, 10, Side.SELL);  // 0
        trade(101_100, 10, Side.BUY);   // +1% again
        trade(100_800, 10, Side.SELL);

        assertThat(texts()).containsExactly("TCS climbs 1% to ₹1,010.00");
    }

    @Test
    void namesTheAggressorOfABlockTradeAndThrottlesRepeats() {
        trade(100_000, 2_50_000, Side.SELL);
        trade(100_000, 5_000, Side.SELL);  // within 20 s: no second block story

        assertThat(texts()).containsExactly("Panic Seller sells a block of 2,50,000 TCS at ₹1,000.00");
    }

    @Test
    void keepsTheNewestFiftyAndTellsSubscribers() {
        List<Headline> heard = new ArrayList<>();
        writer.subscribe(heard::add);
        for (int i = 0; i < 60; i++) {
            writer.file(T0, "Test", null, "story " + i, null, Tone.NEUTRAL);
        }

        assertThat(writer.latest(100)).hasSize(HeadlineWriter.CAPACITY);
        assertThat(writer.latest(1).get(0).text()).isEqualTo("story 59");
        assertThat(heard).hasSize(60);
    }

    @Test
    void haltsAndReopeningsAreFrontPageNews() {
        writer.onEvent(new MarketStatusChanged("TCS", T0, 1, MarketPhase.HALTED,
                "TCS fell 10.4% in 60 seconds (₹1,000.00 → ₹896.00)", T0.plusSeconds(30)));
        writer.onEvent(new MarketStatusChanged("TCS", T0.plusSeconds(30), 2, MarketPhase.OPEN, null, null));

        List<Headline> news = writer.latest(2).reversed();
        assertThat(news.get(0)).extracting(Headline::kicker, Headline::text, Headline::tone)
                .containsExactly("Stop Press", "Circuit breaker halts trading in TCS", Tone.ALERT);
        assertThat(news.get(0).deck()).isEqualTo(
                "TCS fell 10.4% in 60 seconds (₹1,000.00 → ₹896.00). Resting orders stay in the book; trading resumes in 30 seconds.");
        assertThat(news.get(1).text()).isEqualTo("Trading resumes in TCS");
    }

    @Test
    void theAuctionAndTheReopeningPriceMakeTheNews() {
        writer.onEvent(new MarketStatusChanged("TCS", T0, 1, MarketPhase.AUCTION, "call auction", T0.plusSeconds(30)));
        writer.onEvent(new AuctionUpdate("TCS", T0.plusSeconds(10), 2, 91_000L, 130, 150, 130, T0.plusSeconds(30)));
        writer.onEvent(new MarketStatusChanged("TCS", T0.plusSeconds(30), 3, MarketPhase.OPEN, null, null));

        assertThat(texts()).containsExactly("TCS call auction under way", "Trading resumes in TCS at ₹910.00");
        assertThat(writer.latest(1).get(0).deck()).isEqualTo("The call auction crossed 130 shares at one price.");
    }
}
