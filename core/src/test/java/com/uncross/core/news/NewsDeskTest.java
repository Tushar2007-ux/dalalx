package com.uncross.core.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.uncross.core.bots.BotContext;
import com.uncross.core.bots.MarketView;
import com.uncross.core.bots.OrderGateway;
import com.uncross.core.matching.MatchResult;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Made-up company news that moves fair value and sets off a reaction. */
class NewsDeskTest {

    private static final class HandClock extends Clock {
        Instant now = Instant.parse("2026-11-17T10:00:00Z");

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private static final MarketView MARKET = new MarketView() {
        @Override public List<String> symbols() { return List.of("TCS"); }
        @Override public long referencePrice(String symbol) { return 400_000; }
        @Override public long lastPrice(String symbol) { return 400_000; }
        @Override public OptionalLong bestBid(String symbol) { return OptionalLong.empty(); }
        @Override public OptionalLong bestAsk(String symbol) { return OptionalLong.empty(); }
        @Override public List<Long> recentPrices(String symbol, int n) { return List.of(); }
    };

    private final HandClock clock = new HandClock();
    private final List<Long> shifts = new ArrayList<>();
    private final List<OrderRequest> sent = new ArrayList<>();
    private final List<NewsDesk.Flash> flashes = new ArrayList<>();
    private boolean storyRunning;
    private final NewsDesk desk = new NewsDesk((symbol, bp) -> shifts.add(bp), clock, new Random(7),
            () -> storyRunning, Duration.ofSeconds(60), Duration.ofSeconds(120));
    private final BotContext context = new BotContext(MARKET, new OrderGateway() {
        @Override public MatchResult submit(OrderRequest request) { sent.add(request); return null; }
        @Override public com.uncross.core.matching.CancelResult cancel(long orderId) { return null; }
    }, new Random(7));

    NewsDeskTest() {
        desk.onFlash(flashes::add);
    }

    @Test
    void nothingBreaksBeforeTheFirstGap() {
        desk.onTick(context);
        clock.now = clock.now.plusSeconds(59);
        desk.onTick(context);
        assertThat(flashes).isEmpty();
    }

    @Test
    void aStoryMovesFairValueByItsImpactThenTheFloorReacts() {
        desk.breakNews();
        desk.onTick(context);

        NewsDesk.Flash flash = flashes.get(0);
        assertThat(flash.text()).contains("TCS");
        assertThat(Math.abs(flash.impactBp())).isBetween(NewsDesk.MIN_IMPACT_BP, NewsDesk.MAX_IMPACT_BP);
        assertThat(shifts).containsExactly(flash.impactBp());

        for (int i = 0; i < 10; i++) {
            desk.onTick(context);
        }
        assertThat(sent).hasSize(NewsDesk.REACTION_TURNS).allSatisfy(o -> {
            assertThat(o.type()).isEqualTo(OrderType.IOC);
            assertThat(o.traderId()).isEqualTo(flash.good() ? "MOMENTUM" : "PANIC");
        });
    }

    @Test
    void itStaysQuietDuringAStorylineOrWhenSwitchedOff() {
        desk.onTick(context);
        clock.now = clock.now.plusSeconds(200);
        storyRunning = true;
        desk.onTick(context);
        storyRunning = false;
        desk.setEnabled(false);
        desk.onTick(context);

        assertThat(flashes).isEmpty();
    }
}
