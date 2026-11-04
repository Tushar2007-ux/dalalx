package com.dalalx.core.headlines;

import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.EngineListener;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.headlines.Headline.Tone;
import com.dalalx.core.model.Money;
import com.dalalx.core.model.Side;
import com.dalalx.core.model.Trade;
import com.dalalx.core.scenarios.Scenario;
import com.dalalx.core.scenarios.ScenarioDirector;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * The newsroom: turns raw market events into short headlines for The Wire
 * and the headline banner.
 *
 * <p>It is an Observer twice over: an {@link EngineListener} for trades and a
 * {@link ScenarioDirector.ScenarioListener} for storylines. Later features
 * (circuit breaker, surveillance) file their own stories through
 * {@link #file}. Whoever wants the news subscribes with {@link #subscribe}.
 *
 * <p>What makes news:
 * <ul>
 *   <li><b>Big moves</b> — a stock reaching a new whole percent from its
 *       reference price (1%, 2%, ...) in either direction, once per level,
 *       and big pull-backs of two levels or more.</li>
 *   <li><b>Block trades</b> — a single trade of {@value #BLOCK_QTY}+ shares,
 *       at most one per stock every {@code BLOCK_QUIET}.</li>
 *   <li><b>Storylines</b> starting and ending.</li>
 * </ul>
 *
 * <p><b>Thread safety:</b> trades arrive on five worker threads and storylines
 * on the bot thread, so every method that touches state is {@code synchronized}.
 */
public final class HeadlineWriter implements EngineListener, ScenarioDirector.ScenarioListener {

    /** How many headlines The Wire keeps. */
    public static final int CAPACITY = 50;
    static final long BLOCK_QTY = 1_000;
    static final int RETRACE_LEVELS = 2;
    static final Duration BLOCK_QUIET = Duration.ofSeconds(20);

    private final ToLongFunction<String> referencePrice;
    private final Function<String, String> names;
    private final Deque<Headline> wire = new ArrayDeque<>();
    private final Map<String, Integer> announcedLevel = new HashMap<>();
    private final Map<String, Integer> peakUp = new HashMap<>();
    private final Map<String, Integer> peakDown = new HashMap<>();
    private final Map<String, Instant> lastBlock = new HashMap<>();
    private final List<Consumer<Headline>> subscribers = new CopyOnWriteArrayList<>();
    private long nextId;

    /**
     * @param referencePrice the price each stock's move is measured from
     * @param names          trader id → byline ("WHALE" → "The Whale")
     */
    public HeadlineWriter(ToLongFunction<String> referencePrice, Function<String, String> names) {
        this.referencePrice = Objects.requireNonNull(referencePrice, "referencePrice must not be null");
        this.names = Objects.requireNonNull(names, "names must not be null");
    }

    @Override
    public void onEvent(EngineEvent event) {
        if (event instanceof TradeExecuted e) {
            onTrade(e.trade());
        }
    }

    synchronized void onTrade(Trade trade) {
        reportMove(trade);
        reportBlock(trade);
    }

    /** Whole percent from the reference, truncated toward zero: +2.7% → 2, −1.2% → −1. */
    static int level(long price, long reference) {
        return (int) ((price - reference) * 100 / reference);
    }

    /**
     * New records make news: the first time a stock is 1%, 2%, 3%... up (or
     * down) on the day. Pulling back is only news when it gives back at least
     * {@value #RETRACE_LEVELS} levels, so a price wobbling around one line
     * doesn't fill The Wire.
     */
    private void reportMove(Trade trade) {
        String s = trade.symbol();
        int level = level(trade.price(), referencePrice.applyAsLong(s));
        int highest = peakUp.getOrDefault(s, 0);
        int lowest = peakDown.getOrDefault(s, 0);
        int announced = announcedLevel.getOrDefault(s, 0);
        String price = Money.format(trade.price());
        if (level > highest) {
            peakUp.put(s, level);
            announcedLevel.put(s, level);
            file(trade.timestamp(), "Markets", s, s + " climbs " + level + "% to " + price, null, Tone.UP);
        } else if (level < lowest) {
            peakDown.put(s, level);
            announcedLevel.put(s, level);
            file(trade.timestamp(), "Markets", s, s + " slides " + -level + "% to " + price, null, Tone.DOWN);
        } else if (Math.abs(announced) - Math.abs(level) >= RETRACE_LEVELS && Integer.signum(level) != -Integer.signum(announced)) {
            announcedLevel.put(s, level);
            boolean recovering = announced < 0;
            String now = level == 0 ? "back within 1% of its reference" : (level > 0 ? "now up " : "now down ") + Math.abs(level) + "%";
            file(trade.timestamp(), "Markets", s,
                    s + (recovering ? " claws back losses, " : " gives back gains, ") + now + " at " + price,
                    null, recovering ? Tone.UP : Tone.DOWN);
        }
    }

    private void reportBlock(Trade trade) {
        if (trade.qty() < BLOCK_QTY) {
            return;
        }
        Instant last = lastBlock.get(trade.symbol());
        if (last != null && Duration.between(last, trade.timestamp()).compareTo(BLOCK_QUIET) < 0) {
            return;
        }
        lastBlock.put(trade.symbol(), trade.timestamp());
        boolean buying = trade.aggressorSide() == Side.BUY;
        String who = names.apply(buying ? trade.buyerId() : trade.sellerId());
        file(trade.timestamp(), "Block trade", trade.symbol(),
                who + (buying ? " buys " : " sells ") + "a block of " + Money.groupIndian(trade.qty())
                        + " " + trade.symbol() + " at " + Money.format(trade.price()),
                null, buying ? Tone.UP : Tone.DOWN);
    }

    @Override
    public synchronized void started(Scenario scenario, Instant at) {
        file(at, "Stop Press", symbolOrNull(scenario), "Now running: " + scenario.headline(), scenario.deck(), Tone.ALERT);
    }

    @Override
    public synchronized void ended(Scenario scenario, Instant at, boolean completed) {
        file(at, "Latest", symbolOrNull(scenario),
                (completed ? "Story over: " : "Story pulled: ") + scenario.headline() + "; the market returns to normal",
                null, Tone.NEUTRAL);
    }

    private static String symbolOrNull(Scenario scenario) {
        return "ALL".equals(scenario.symbol()) ? null : scenario.symbol();
    }

    /** Files a story on The Wire and tells every subscriber. Used by later features too. */
    public synchronized Headline file(Instant time, String kicker, String symbol, String text, String deck, Tone tone) {
        Headline headline = new Headline(++nextId, time, kicker, symbol, text, deck, tone);
        wire.addFirst(headline);
        if (wire.size() > CAPACITY) {
            wire.removeLast();
        }
        subscribers.forEach(s -> s.accept(headline));
        return headline;
    }

    /** @return up to {@code limit} headlines, newest first */
    public synchronized List<Headline> latest(int limit) {
        return wire.stream().limit(Math.max(0, limit)).toList();
    }

    /** Calls {@code subscriber} with every new headline (on whatever thread filed it). */
    public void subscribe(Consumer<Headline> subscriber) {
        subscribers.add(Objects.requireNonNull(subscriber, "subscriber must not be null"));
    }
}
