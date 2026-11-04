package com.dalalx.core.events;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Delivers engine events to every registered {@link EngineListener}.
 *
 * <p><b>Observer pattern:</b> this is the <em>subject</em> side. Publishers
 * call {@link #publish}; the bus forwards each event to all listeners, in the
 * order they registered.
 *
 * <p><b>Why {@link CopyOnWriteArrayList}?</b> Events are published constantly
 * by several worker threads, while listeners are registered rarely. This list
 * copies itself on every write but gives lock-free, never-failing iteration on
 * reads, so a listener registering mid-publish can't cause a
 * {@code ConcurrentModificationException}. That trade-off (cheap reads,
 * expensive writes) is exactly right here.
 *
 * <p><b>Isolation:</b> if one listener throws, the error is logged and the
 * other listeners still get the event. A bug in, say, the headline writer must
 * never stop trades reaching the screen.
 *
 * <p>The bus also hands out the global event sequence numbers, from an
 * {@link AtomicLong} so that several symbols can number events at once without
 * duplicates.
 */
public final class EventBus {

    private static final Logger LOG = System.getLogger(EventBus.class.getName());

    private final List<EngineListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    /** Adds a listener; it receives every event published from now on. */
    public void register(EngineListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /** Removes a listener. Does nothing if it was not registered. */
    public void unregister(EngineListener listener) {
        listeners.remove(listener);
    }

    /** Sends {@code event} to every listener; one failing listener doesn't affect the rest. */
    public void publish(EngineEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        for (EngineListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "listener " + listener + " failed on " + event, e);
            }
        }
    }

    /** @return the next global event number (thread-safe, strictly increasing) */
    public long nextSequence() {
        return sequence.incrementAndGet();
    }

    /** @return how many listeners are registered */
    public int listenerCount() {
        return listeners.size();
    }
}
