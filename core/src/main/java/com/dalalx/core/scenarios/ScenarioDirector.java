package com.dalalx.core.scenarios;

import com.dalalx.core.bots.BotContext;
import com.dalalx.core.bots.BotRunner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs at most one {@link Scenario} at a time, on the bot thread.
 *
 * <p>It is a {@link BotRunner.TickHook}: every tick it applies any start/stop
 * request, steps the running story, and ends it when its time is up. Web
 * threads never touch the running story directly; they drop a request in a
 * concurrent queue (the same hand-off idea as the order workers) and read the
 * {@code volatile} {@link Status} snapshot.
 *
 * <p>Interested parties (the headline writer) are told when a story starts
 * and ends through {@link ScenarioListener} (Observer).
 */
public final class ScenarioDirector implements BotRunner.TickHook {

    /** What is running now, for the UI. {@code null} fields mean nothing is. */
    public record Status(String scenarioId, Instant startedAt, Duration duration) {
        static final Status IDLE = new Status(null, null, null);

        public boolean running() {
            return scenarioId != null;
        }
    }

    /** Told about stories starting and ending (called on the bot thread). */
    public interface ScenarioListener {
        void started(Scenario scenario, Instant at);

        void ended(Scenario scenario, Instant at, boolean completed);
    }

    private sealed interface Request permits Start, Stop {
    }

    private record Start(Scenario scenario) implements Request {
    }

    private record Stop() implements Request {
    }

    private final Map<String, Scenario> catalogue;
    private final BotRunner runner;
    private final Clock clock;
    private final Queue<Request> requests = new ConcurrentLinkedQueue<>();
    private final List<ScenarioListener> listeners = new CopyOnWriteArrayList<>();
    private volatile Status status = Status.IDLE;

    // Bot-thread-only state:
    private Scenario current;
    private ScenarioStage stage;
    private Instant startedAt;

    public ScenarioDirector(List<Scenario> scenarios, BotRunner runner, Clock clock) {
        this.catalogue = scenarios.stream().collect(Collectors.toMap(Scenario::id, Function.identity(),
                (a, b) -> { throw new IllegalArgumentException("duplicate scenario " + a.id()); },
                java.util.LinkedHashMap::new));
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** @return every storyline, in catalogue order */
    public List<Scenario> scenarios() {
        return List.copyOf(catalogue.values());
    }

    /** @return the storyline with that id, if any */
    public Optional<Scenario> scenario(String id) {
        return Optional.ofNullable(catalogue.get(id));
    }

    /** Asks for a story to start at the next tick (replacing any running one). @return false if unknown */
    public boolean start(String scenarioId) {
        Scenario scenario = catalogue.get(scenarioId);
        if (scenario == null) {
            return false;
        }
        requests.add(new Start(scenario));
        return true;
    }

    /** Asks for the running story to stop at the next tick. */
    public void stop() {
        requests.add(new Stop());
    }

    /** @return what is running now */
    public Status status() {
        return status;
    }

    public void addListener(ScenarioListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    @Override
    public void onTick(BotContext context) {
        for (Request r; (r = requests.poll()) != null; ) {
            finish(false);
            if (r instanceof Start s) {
                begin(s.scenario(), context);
            }
        }
        if (current == null) {
            return;
        }
        Duration elapsed = Duration.between(startedAt, clock.instant());
        if (elapsed.compareTo(current.duration()) >= 0) {
            finish(true);
        } else {
            current.step(stage, elapsed);
        }
    }

    private void begin(Scenario scenario, BotContext context) {
        current = scenario;
        stage = new ScenarioStage(context, runner);
        startedAt = clock.instant();
        scenario.begin(stage);
        status = new Status(scenario.id(), startedAt, scenario.duration());
        listeners.forEach(l -> l.started(scenario, startedAt));
    }

    private void finish(boolean completed) {
        if (current == null) {
            return;
        }
        Scenario ended = current;
        ended.end(stage);
        stage.restore();
        current = null;
        stage = null;
        status = Status.IDLE;
        Instant now = clock.instant();
        listeners.forEach(l -> l.ended(ended, now, completed));
    }
}
