package com.dalalx.core.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test helper: an {@link EngineListener} that simply remembers every event it
 * receives, so a test can check what was published and in what order.
 */
public final class RecordingListener implements EngineListener {

    private final List<EngineEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void onEvent(EngineEvent event) {
        events.add(event);
    }

    /** @return every event received so far, oldest first */
    public List<EngineEvent> events() {
        return List.copyOf(events);
    }

    /** @return only the events of one type, oldest first */
    public <T extends EngineEvent> List<T> eventsOf(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** @return the simple class names of the events, e.g. ["OrderAccepted", "TradeExecuted"] */
    public List<String> types() {
        return events.stream().map(e -> e.getClass().getSimpleName()).toList();
    }

    public void clear() {
        events.clear();
    }
}
