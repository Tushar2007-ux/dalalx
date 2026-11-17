package com.uncross.core.scenarios;

import com.uncross.core.bots.BotTuning;
import com.uncross.core.bots.MarketMakerBot;
import com.uncross.core.bots.MomentumBot;
import com.uncross.core.bots.PanicSellerBot;
import com.uncross.core.bots.WhaleBot;
import com.uncross.core.model.Side;
import java.time.Duration;
import java.util.List;

/**
 * Today's five storylines. Each is a small {@link Scenario} class; the
 * abstract {@link Base} holds the newspaper fields they all share, so each
 * story only writes its plot ({@code begin} and {@code step}).
 */
public final class Storylines {

    private Storylines() {
    }

    /** @return fresh instances of every storyline */
    public static List<Scenario> all() {
        return List.of(new FlashCrash(), new Bubble(), new WhaleDump(), new CalmMarket(), new TippedOff());
    }

    /** Holds the newspaper fields so each storyline only implements its plot. */
    abstract static class Base implements Scenario {
        private final String id;
        private final String kicker;
        private final String headline;
        private final String deck;
        private final String symbol;
        private final Tone tone;
        private final Duration duration;

        Base(String id, String kicker, String headline, String deck, String symbol, Tone tone, Duration duration) {
            this.id = id;
            this.kicker = kicker;
            this.headline = headline;
            this.deck = deck;
            this.symbol = symbol;
            this.tone = tone;
            this.duration = duration;
        }

        @Override public String id() { return id; }
        @Override public String kicker() { return kicker; }
        @Override public String headline() { return headline; }
        @Override public String deck() { return deck; }
        @Override public String symbol() { return symbol; }
        @Override public Tone tone() { return tone; }
        @Override public Duration duration() { return duration; }
    }

    /**
     * Panic Seller dumps RELIANCE into a thin book. The Munim keeps quoting
     * but small and fast, so each dump sweeps its levels and the price walks
     * down; Momentum Chaser joins in. Steep enough to trip a 10% breaker.
     */
    static final class FlashCrash extends Base {
        static final long DUMP_QTY = 1_500;
        static final long DUMP_BP = -200;

        FlashCrash() {
            super("flash-crash", "Markets", "Flash Crash: RELIANCE loses its footing in ninety seconds",
                    "Panic Seller dumps into a thin book while The Munim quotes small. Watch how fast liquidity disappears.",
                    "RELIANCE", Tone.DOWN, Duration.ofSeconds(90));
        }

        @Override
        public void begin(ScenarioStage stage) {
            stage.tune(MarketMakerBot.ID, new BotTuning(4, 0));
            stage.tune(MomentumBot.ID, new BotTuning(4, 2));
        }

        @Override
        public void step(ScenarioStage stage, Duration elapsed) {
            stage.sweep(PanicSellerBot.ID, symbol(), Side.SELL, DUMP_BP, DUMP_QTY);
        }
    }

    /** Momentum buying feeds on itself in INFY, then the bid disappears and Panic Seller takes over. */
    static final class Bubble extends Base {
        static final Duration TURN = Duration.ofSeconds(80);

        Bubble() {
            super("bubble", "Speculation", "Bubble: INFY runs up on nothing but momentum",
                    "Momentum Chaser feeds on its own buying until the bid runs out. Can you get out at the top?",
                    "INFY", Tone.UP, Duration.ofSeconds(120));
        }

        @Override
        public void begin(ScenarioStage stage) {
            stage.tune(MarketMakerBot.ID, new BotTuning(4, 0));
            stage.tune(MomentumBot.ID, new BotTuning(4, 3));
        }

        @Override
        public void step(ScenarioStage stage, Duration elapsed) {
            if (elapsed.compareTo(TURN) < 0) {
                stage.sweep(MomentumBot.ID, symbol(), Side.BUY, 100, 600);
            } else {
                stage.sweep(PanicSellerBot.ID, symbol(), Side.SELL, -200, 1_200);
            }
        }
    }

    /** The Whale unloads two lakh HDFCBANK shares in slices: a steady seller visible in the tape. */
    static final class WhaleDump extends Base {
        static final long TOTAL = 200_000;
        static final long SLICE = 400;
        private long sold;

        WhaleDump() {
            super("whale-dump", "Block trades", "Whale Dump: 2 lakh HDFCBANK shares hit the market",
                    "The Whale unloads in slices. Spot the pattern in the tape before the price gives way.",
                    "HDFCBANK", Tone.DOWN, Duration.ofSeconds(180));
        }

        @Override
        public void begin(ScenarioStage stage) {
            sold = 0;
            stage.pause(WhaleBot.ID); // its own parent order would get in the way
        }

        @Override
        public void step(ScenarioStage stage, Duration elapsed) {
            if (sold < TOTAL) {
                sold += stage.sweep(WhaleBot.ID, symbol(), Side.SELL, -30, Math.min(SLICE, TOTAL - sold)).filledQty();
            }
        }
    }

    /** Everyone trades gently and Panic Seller takes the day off. */
    static final class CalmMarket extends Base {
        CalmMarket() {
            super("calm", "Quiet day", "Calm Market: tight spreads, small moves, steady tape",
                    "Every correspondent trades gently. A good session for practising limit orders.",
                    "ALL", Tone.NEUTRAL, Duration.ofMinutes(10));
        }

        @Override
        public void begin(ScenarioStage stage) {
            stage.tune(MarketMakerBot.ID, new BotTuning(2, 1));
            stage.tune(MomentumBot.ID, new BotTuning(1, 0));
            stage.tune(WhaleBot.ID, new BotTuning(0, 0));
            stage.pause(PanicSellerBot.ID);
        }

        @Override
        public void step(ScenarioStage stage, Duration elapsed) {
            // Nothing to do each beat: the calm comes from the dials set in begin().
        }
    }

    /**
     * A clerk who knows the Whale is about to buy a block of TCS buys first,
     * waits for the block to push the price up, then sells into it. The
     * surveillance desk's front-running rule should open a case on the clerk.
     */
    static final class TippedOff extends Base {
        static final String CLERK = "CLERK";
        static final long CLERK_QTY = 800;
        static final Duration BLOCK_STARTS = Duration.ofSeconds(4);
        static final Duration CLERK_SELLS = Duration.ofSeconds(14);
        static final Duration BLOCK_ENDS = Duration.ofSeconds(30);
        static final long BLOCK_SLICE = 600;
        private long held;
        private boolean bought;

        TippedOff() {
            super("tipped-off", "Investigation", "Tipped Off: someone knew the Whale was coming",
                    "A clerk buys TCS seconds before a block order, then sells into it. Will surveillance notice?",
                    "TCS", Tone.UP, Duration.ofSeconds(45));
        }

        @Override
        public void begin(ScenarioStage stage) {
            held = 0;
            bought = false;
            stage.pause(WhaleBot.ID); // the block below is the Whale's; its own routine would muddle it
        }

        @Override
        public void step(ScenarioStage stage, Duration elapsed) {
            if (!bought) {
                held = stage.sweep(CLERK, symbol(), Side.BUY, 30, CLERK_QTY).filledQty();
                bought = true;
            } else if (elapsed.compareTo(BLOCK_STARTS) >= 0 && elapsed.compareTo(BLOCK_ENDS) < 0) {
                stage.sweep(WhaleBot.ID, symbol(), Side.BUY, 40, BLOCK_SLICE);
            }
            if (held > 0 && elapsed.compareTo(CLERK_SELLS) >= 0) {
                // The bids trail the Whale's buying, so the clerk reaches down to them, a little at a time.
                held -= stage.sweep(CLERK, symbol(), Side.SELL, -150, held).filledQty();
            }
        }
    }
}
