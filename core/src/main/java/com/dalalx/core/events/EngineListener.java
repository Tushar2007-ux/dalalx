package com.dalalx.core.events;

/**
 * Something that wants to hear about engine events: the WebSocket bridge, the
 * candle aggregator, surveillance, the headline writer, bots.
 *
 * <p>This is the <b>Observer</b> in the Observer pattern. The engine (the
 * <em>subject</em>) knows only this one-method interface, never the concrete
 * listeners. That keeps the dependency pointing one way: the UI, surveillance
 * and headlines depend on the engine, but the engine depends on none of them,
 * and new listeners can be added without touching engine code.
 *
 * <p>A functional interface, so a lambda works: {@code bus.register(e -> log(e))}.
 *
 * <p><b>Threading:</b> {@link #onEvent} is called on the publishing thread (a
 * symbol's worker). It must return quickly; slow work should be handed to the
 * listener's own thread or queue.
 */
@FunctionalInterface
public interface EngineListener {

    /** @param event what happened */
    void onEvent(EngineEvent event);
}
