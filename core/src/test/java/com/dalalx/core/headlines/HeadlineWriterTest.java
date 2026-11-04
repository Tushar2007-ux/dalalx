package com.dalalx.core.headlines;

import static org.assertj.core.api.Assertions.assertThat;

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
}
