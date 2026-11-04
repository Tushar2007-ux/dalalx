package com.dalalx.core.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.book.BookSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventBusTest {

    private final EventBus bus = new EventBus();

    private EngineEvent anEvent() {
        return new BookChanged("TCS", Instant.EPOCH, bus.nextSequence(), new BookSnapshot("TCS", List.of(), List.of()));
    }

    @Test
    void everyListenerReceivesEveryEventInRegistrationOrder() {
        List<String> calls = new ArrayList<>();
        bus.register(e -> calls.add("first"));
        bus.register(e -> calls.add("second"));

        bus.publish(anEvent());
        bus.publish(anEvent());

        assertThat(calls).containsExactly("first", "second", "first", "second");
    }

    @Test
    void unregisteredListenerStopsReceiving() {
        RecordingListener listener = new RecordingListener();
        bus.register(listener);
        bus.publish(anEvent());

        bus.unregister(listener);
        bus.publish(anEvent());

        assertThat(listener.events()).hasSize(1);
        assertThat(bus.listenerCount()).isZero();
    }

    @Test
    void aFailingListenerDoesNotStopTheOthers() {
        RecordingListener before = new RecordingListener();
        RecordingListener after = new RecordingListener();
        bus.register(before);
        bus.register(e -> { throw new IllegalStateException("buggy listener"); });
        bus.register(after);

        bus.publish(anEvent());

        assertThat(before.events()).hasSize(1);
        assertThat(after.events()).hasSize(1);
    }

    @Test
    void aListenerCanRegisterAnotherWhilePublishingWithoutCrashing() {
        RecordingListener late = new RecordingListener();
        bus.register(e -> bus.register(late)); // modifies the list mid-iteration

        bus.publish(anEvent());

        assertThat(bus.listenerCount()).isEqualTo(2); // CopyOnWriteArrayList: no ConcurrentModificationException
    }

    @Test
    void sequenceNumbersStrictlyIncrease() {
        long a = bus.nextSequence();
        long b = bus.nextSequence();

        assertThat(b).isGreaterThan(a);
    }
}
