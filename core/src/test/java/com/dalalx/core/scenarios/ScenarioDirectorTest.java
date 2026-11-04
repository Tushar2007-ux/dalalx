package com.dalalx.core.scenarios;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.bots.BotContext;
import com.dalalx.core.bots.BotRunner;
import com.dalalx.core.bots.BotStatus;
import com.dalalx.core.bots.BotTuning;
import com.dalalx.core.bots.MarketView;
import com.dalalx.core.bots.MomentumBot;
import com.dalalx.core.bots.OrderGateway;
import com.dalalx.core.bots.PanicSellerBot;
import com.dalalx.core.matching.CancelResult;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ScenarioDirectorTest {

    /** A storyline that records its beats and turns two bots' dials. */
    private static final class Script implements Scenario {
        final List<String> beats = new ArrayList<>();

        @Override public String id() { return "script"; }
        @Override public String kicker() { return "Test"; }
        @Override public String headline() { return "A test story"; }
        @Override public String deck() { return "deck"; }
        @Override public String symbol() { return "TCS"; }
        @Override public Tone tone() { return Tone.NEUTRAL; }
        @Override public Duration duration() { return Duration.ofSeconds(10); }

        @Override
        public void begin(ScenarioStage stage) {
            beats.add("begin");
            stage.tune(MomentumBot.ID, new BotTuning(4, 4));
            stage.pause(PanicSellerBot.ID);
        }

        @Override
        public void step(ScenarioStage stage, Duration elapsed) {
            beats.add("step@" + elapsed.toSeconds());
        }

        @Override
        public void end(ScenarioStage stage) {
            beats.add("end");
        }
    }

    private static final MarketView NO_MARKET = new MarketView() {
        @Override public List<String> symbols() { return List.of("TCS"); }
        @Override public long referencePrice(String s) { return 100; }
        @Override public long lastPrice(String s) { return 100; }
        @Override public OptionalLong bestBid(String s) { return OptionalLong.empty(); }
        @Override public OptionalLong bestAsk(String s) { return OptionalLong.empty(); }
        @Override public List<Long> recentPrices(String s, int n) { return List.of(); }
    };

    private static final OrderGateway NO_ORDERS = new OrderGateway() {
        @Override public MatchResult submit(OrderRequest r) { throw new UnsupportedOperationException(); }
        @Override public CancelResult cancel(long id) { return CancelResult.unknown(id); }
    };

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T09:15:00Z"));
    private final MomentumBot momentum = new MomentumBot();
    private final PanicSellerBot panic = new PanicSellerBot();
    // aggressiveness 0 so the real bots never act (they'd need a market); only the hook runs.
    private final BotRunner runner = new BotRunner(List.of(momentum, panic), NO_MARKET, NO_ORDERS, new Random(1), 100);
    private final Script script = new Script();
    private final ScenarioDirector director = new ScenarioDirector(List.of(script), runner, clock);
    private final List<String> news = new ArrayList<>();
    private final BotContext context = new BotContext(NO_MARKET, NO_ORDERS, new Random(1));

    ScenarioDirectorTest() {
        momentum.tune(new BotTuning(0, 2));
        panic.tune(new BotTuning(0, 2));
        runner.addHook(director);
        director.addListener(new ScenarioDirector.ScenarioListener() {
            @Override public void started(Scenario s, Instant at) { news.add("started " + s.id()); }
            @Override public void ended(Scenario s, Instant at, boolean completed) { news.add("ended " + completed); }
        });
    }

    private void tickAfter(int seconds) {
        clock.advance(Duration.ofSeconds(seconds));
        director.onTick(context);
    }

    @Test
    void runsBeginThenStepsThenEndsWhenTimeIsUp() {
        assertThat(director.start("script")).isTrue();
        director.onTick(context);           // begins (and the first step at 0s)
        tickAfter(4);
        tickAfter(4);
        assertThat(director.status().running()).isTrue();
        tickAfter(4);                     // 12s > 10s: over

        assertThat(script.beats).containsExactly("begin", "step@0", "step@4", "step@8", "end");
        assertThat(news).containsExactly("started script", "ended true");
        assertThat(director.status().running()).isFalse();
    }

    @Test
    void restoresEveryDialAndPauseWhenTheStoryEnds() {
        director.start("script");
        director.onTick(context);
        assertThat(momentum.tuning()).isEqualTo(new BotTuning(4, 4));
        assertThat(runner.status(PanicSellerBot.ID)).contains(BotStatus.PAUSED);

        director.stop();
        director.onTick(context);

        assertThat(momentum.tuning()).isEqualTo(new BotTuning(0, 2));
        assertThat(runner.status(PanicSellerBot.ID)).contains(BotStatus.ACTIVE);
        assertThat(news).containsExactly("started script", "ended false");
    }

    @Test
    void unknownStoriesAreRefused() {
        assertThat(director.start("nope")).isFalse();
        assertThat(director.scenario("script")).contains(script);
    }

    @Test
    void theStandardCatalogueHasFourStories() {
        assertThat(Storylines.all()).extracting(Scenario::id)
                .containsExactly("flash-crash", "bubble", "whale-dump", "calm");
    }
}
