package com.dalalx.core.scenarios;

import java.time.Duration;

/**
 * A scripted storyline ("Flash Crash", "Bubble"...) that bends the market for
 * a few minutes so a particular behaviour can be watched and explained.
 *
 * <p>Each storyline is one implementation of this interface (Strategy again);
 * the {@link ScenarioDirector} runs whichever one was chosen and calls the
 * three hooks in order: {@code begin} once, {@code step} every tick, {@code end}
 * once. Scenarios act only through a {@link ScenarioStage}, never on the
 * exchange directly.
 */
public interface Scenario {

    /** Up, down or neither: which arrow the newspaper prints. */
    enum Tone { UP, DOWN, NEUTRAL }

    /** @return a stable id for the API, e.g. {@code "flash-crash"} */
    String id();

    /** @return the small heading above the headline, e.g. "Markets" */
    String kicker();

    /** @return the story's headline */
    String headline();

    /** @return one or two sentences on what to watch for */
    String deck();

    /** @return the symbol the story is about, or "ALL" */
    String symbol();

    Tone tone();

    /** @return how long the story runs before the market returns to normal */
    Duration duration();

    /** Sets the scene (tune or pause bots). */
    void begin(ScenarioStage stage);

    /** One beat of the story; {@code elapsed} is the time since it began. */
    void step(ScenarioStage stage, Duration elapsed);

    /** Anything extra to undo; the stage restores bot dials and pauses by itself. */
    default void end(ScenarioStage stage) {
    }
}
