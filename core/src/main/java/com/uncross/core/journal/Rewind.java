package com.uncross.core.journal;

import com.uncross.core.breaker.BreakerRule;
import com.uncross.core.events.EngineEvent;
import com.uncross.core.events.EngineListener;
import com.uncross.core.events.EventBus;
import com.uncross.core.events.MarketStatusChanged;
import com.uncross.core.events.TradeExecuted;
import com.uncross.core.exchange.Exchange;
import com.uncross.core.exchange.ListedStock;
import com.uncross.core.model.Trade;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Plays a past day back like a video: the journal's commands are fed, at a
 * chosen speed, into a <em>separate</em> exchange, and you watch its prices
 * and trades.
 *
 * <p>The rewind exchange is a complete, private copy of the machinery (its own
 * workers, books, breaker and event bus), so nothing it does can touch the
 * live market. Building a second exchange is one line, because the
 * {@link Exchange} depends only on what it is given: a list of stocks, an
 * event bus and a clock.
 *
 * <p><b>Threads:</b> one "rewind" thread reads the journal and sleeps between
 * entries (the real gap divided by the speed, and never more than
 * {@link #LONGEST_WAIT}, so quiet stretches skip). The exchange's own workers
 * do the matching. The picture of the market ({@link View}) is gathered by a
 * listener and read by the web, so its methods are {@code synchronized}.
 */
public final class Rewind implements AutoCloseable {

    /** Where the tape is. */
    public enum State { PLAYING, PAUSED, FINISHED, STOPPED }

    /** One stock as the rewind sees it at the moment. */
    public record StockLine(String symbol, long open, long last, long high, long low, long volume,
                            List<Long> trail, String phase) {
    }

    /** One trade on the rewind's tape. */
    public record TapeLine(Instant at, String symbol, long price, long qty, String buyerId, String sellerId) {
    }

    /** Everything the Rewind page draws, as of now. */
    public record View(String session, State state, int speed, Instant at, int done, int total,
                       List<StockLine> stocks, List<TapeLine> tape) {
    }

    public static final int MAX_SPEED = 600;
    static final Duration LONGEST_WAIT = Duration.ofMillis(1500);
    static final int TRAIL = 80;
    static final int TAPE = 30;

    private final String session;
    private final List<JournalEntry> entries;
    private final ReplayClock clock = new ReplayClock(Clock.systemUTC());
    private final Exchange exchange;
    private final Replayer replayer;
    private final Picture picture = new Picture();
    private final Thread player;
    private volatile int speed;
    private volatile State state = State.PLAYING;
    private volatile int done;

    public Rewind(String session, List<JournalEntry> entries, List<ListedStock> startingStocks, int speed) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.entries = List.copyOf(entries);
        setSpeed(speed);
        EventBus bus = new EventBus();
        bus.register(picture);
        this.exchange = new Exchange(startingStocks, bus, clock, BreakerRule.STANDARD);
        exchange.onListing(stock -> picture.listed(stock.symbol()));
        startingStocks.forEach(stock -> picture.listed(stock.symbol()));
        this.replayer = new Replayer(exchange, clock, opened -> { });
        this.player = new Thread(this::play, "rewind");
        player.setDaemon(true);
    }

    /** Starts playing (on its own thread) and returns at once. */
    public Rewind start() {
        exchange.start();
        player.start();
        return this;
    }

    private void play() {
        Instant previous = entries.isEmpty() ? null : entries.get(0).at();
        try {
            for (JournalEntry entry : entries) {
                waitWhilePaused();
                waitFor(Duration.between(previous, entry.at()));
                if (state == State.STOPPED) {
                    return;
                }
                replayer.apply(entry);
                picture.at(entry.at());
                previous = entry.at();
                done++;
            }
            state = State.FINISHED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            state = State.FINISHED; // a damaged tape ends the show; it never takes the server down
        }
    }

    private void waitFor(Duration realGap) throws InterruptedException {
        long millis = Math.min(LONGEST_WAIT.toMillis(), Math.max(0, realGap.toMillis() / speed));
        if (millis > 0) {
            TimeUnit.MILLISECONDS.sleep(millis);
        }
    }

    private void waitWhilePaused() throws InterruptedException {
        while (state == State.PAUSED) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
    }

    public void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    public void resume() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
        }
    }

    public void setSpeed(int newSpeed) {
        if (newSpeed < 1 || newSpeed > MAX_SPEED) {
            throw new IllegalArgumentException("speed must be 1–" + MAX_SPEED + "×");
        }
        this.speed = newSpeed;
    }

    public View view() {
        return picture.view(session, state, speed, done, entries.size());
    }

    public State state() {
        return state;
    }

    /** Stops the tape and shuts the rewind's private exchange down. */
    @Override
    public void close() throws InterruptedException {
        state = State.STOPPED;
        player.interrupt();
        player.join(2_000);
        exchange.shutdown(2, TimeUnit.SECONDS);
    }

    /** Listens to the rewind exchange and keeps the picture the page draws. */
    private static final class Picture implements EngineListener {

        private static final class Stock {
            long open;
            long last;
            long high;
            long low;
            long volume;
            String phase = "OPEN";
            final Deque<Long> trail = new ArrayDeque<>();
        }

        private final Map<String, Stock> stocks = new LinkedHashMap<>();
        private final Deque<TapeLine> tape = new ArrayDeque<>();
        private Instant at;

        synchronized void listed(String symbol) {
            stocks.putIfAbsent(symbol, new Stock());
        }

        synchronized void at(Instant moment) {
            this.at = moment;
        }

        @Override
        public synchronized void onEvent(EngineEvent event) {
            if (event instanceof TradeExecuted e) {
                trade(e.trade());
            } else if (event instanceof MarketStatusChanged e) {
                Stock s = stocks.get(e.symbol());
                if (s != null) {
                    s.phase = e.phase().name();
                }
            }
        }

        private void trade(Trade t) {
            Stock s = stocks.computeIfAbsent(t.symbol(), k -> new Stock());
            if (s.open == 0) {
                s.open = t.price();
                s.high = t.price();
                s.low = t.price();
            }
            s.last = t.price();
            s.high = Math.max(s.high, t.price());
            s.low = Math.min(s.low, t.price());
            s.volume += t.qty();
            s.trail.addLast(t.price());
            if (s.trail.size() > TRAIL) {
                s.trail.removeFirst();
            }
            tape.addFirst(new TapeLine(t.timestamp(), t.symbol(), t.price(), t.qty(), t.buyerId(), t.sellerId()));
            if (tape.size() > TAPE) {
                tape.removeLast();
            }
        }

        synchronized View view(String session, State state, int speed, int done, int total) {
            List<StockLine> lines = new ArrayList<>();
            stocks.forEach((symbol, s) -> lines.add(new StockLine(symbol, s.open, s.last, s.high, s.low, s.volume,
                    List.copyOf(s.trail), s.phase)));
            return new View(session, state, speed, at, done, total, lines, List.copyOf(tape));
        }
    }
}
