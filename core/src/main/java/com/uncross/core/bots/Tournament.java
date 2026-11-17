package com.uncross.core.bots;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

/**
 * A timed contest between the bots: who makes the most money in the next
 * few minutes?
 *
 * <p>At the start it writes down each bot's profit so far; at any moment a
 * bot's score is its profit now minus that figure. When the time is up the
 * scores are frozen, the winner is announced to the listeners (the newspaper
 * runs it as a headline), and the result stays up until the next round.
 *
 * <p>The tournament never trades and never talks to the engine. It only
 * reads profit figures through a function it is given, so it can be tested
 * with a made-up ledger, and it would work just the same for any kind of
 * trader. It finishes itself from the bots' clock: it is a
 * {@link BotRunner.TickHook}.
 */
public final class Tournament implements BotRunner.TickHook {

    public enum State { IDLE, RUNNING, FINISHED }

    /** One bot's place in the table. */
    public record Score(int rank, String traderId, String name, long pnl) {
    }

    /** The table, as of now. */
    public record Standings(State state, Instant startedAt, Instant endsAt, List<Score> scores) {
        public Optional<Score> leader() {
            return scores.isEmpty() ? Optional.empty() : Optional.of(scores.get(0));
        }
    }

    public static final Duration SHORTEST = Duration.ofSeconds(30);
    public static final Duration LONGEST = Duration.ofMinutes(30);

    private final BotRunner runner;
    private final ToLongFunction<String> pnlOf;
    private final Clock clock;
    private final List<Consumer<Standings>> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Long> startingPnl = new LinkedHashMap<>();
    private State state = State.IDLE;
    private Instant startedAt;
    private Instant endsAt;
    private List<Score> finalScores = List.of();

    public Tournament(BotRunner runner, ToLongFunction<String> pnlOf, Clock clock) {
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.pnlOf = Objects.requireNonNull(pnlOf, "pnlOf must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Starts a round of {@code length}; any round already running is replaced. */
    public synchronized Standings start(Duration length) {
        if (length.compareTo(SHORTEST) < 0 || length.compareTo(LONGEST) > 0) {
            throw new IllegalArgumentException("a round lasts 30 seconds to 30 minutes");
        }
        startingPnl.clear();
        for (TradingBot bot : runner.bots()) {
            startingPnl.put(bot.id(), pnlOf.applyAsLong(bot.id()));
        }
        startedAt = clock.instant();
        endsAt = startedAt.plus(length);
        state = State.RUNNING;
        finalScores = List.of();
        return standings();
    }

    /** Ends the round now, without a winner. */
    public synchronized void cancel() {
        state = State.IDLE;
        startingPnl.clear();
    }

    @Override
    public void onTick(BotContext context) {
        Standings finished = null;
        synchronized (this) {
            if (state == State.RUNNING && !clock.instant().isBefore(endsAt)) {
                finalScores = scoresNow();
                state = State.FINISHED;
                finished = standings();
            }
        }
        if (finished != null) {
            Standings result = finished;
            listeners.forEach(listener -> listener.accept(result)); // outside the lock: listeners may be slow
        }
    }

    public synchronized Standings standings() {
        List<Score> scores = switch (state) {
            case RUNNING -> scoresNow();
            case FINISHED -> finalScores;
            case IDLE -> List.of();
        };
        return new Standings(state, startedAt, endsAt, scores);
    }

    private List<Score> scoresNow() {
        record Raw(String id, long pnl) {
        }
        List<Raw> raw = new ArrayList<>();
        // Only bots entered at the start can win; one hired half-way through is not in this round.
        startingPnl.forEach((id, start) -> raw.add(new Raw(id, pnlOf.applyAsLong(id) - start)));
        raw.sort(Comparator.comparingLong(Raw::pnl).reversed().thenComparing(Raw::id));
        List<Score> scores = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            scores.add(new Score(i + 1, raw.get(i).id(), runner.nameOf(raw.get(i).id()), raw.get(i).pnl()));
        }
        return List.copyOf(scores);
    }

    /** Called once when a round finishes, with the final table. */
    public void onFinish(Consumer<Standings> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }
}
