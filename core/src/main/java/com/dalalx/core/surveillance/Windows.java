package com.dalalx.core.surveillance;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Small helpers shared by the rules: a per-key sliding window of timestamped
 * entries, and a per-key cooldown so one burst of abuse opens one case, not
 * fifty.
 */
final class Windows {

    private Windows() {
    }

    /** Something with a time, so a window can drop it when it gets old. */
    interface Timed {
        Instant time();
    }

    /** Per-key deques that forget entries older than {@code length}. */
    static final class Sliding<K, V extends Timed> {
        private final Duration length;
        private final Map<K, Deque<V>> windows = new HashMap<>();

        Sliding(Duration length) {
            this.length = length;
        }

        /** Adds an entry and returns the key's window with old entries removed. */
        Deque<V> add(K key, V value) {
            Deque<V> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
            window.addLast(value);
            prune(window, value.time());
            return window;
        }

        /** @return the key's window as of {@code now} (possibly empty) */
        Deque<V> get(K key, Instant now) {
            Deque<V> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
            prune(window, now);
            return window;
        }

        void clear(K key) {
            windows.remove(key);
        }

        private void prune(Deque<V> window, Instant now) {
            Instant cutoff = now.minus(length);
            while (!window.isEmpty() && window.peekFirst().time().isBefore(cutoff)) {
                window.removeFirst();
            }
        }
    }

    /** Remembers when each key last produced a case. */
    static final class Cooldown<K> {
        private final Duration quiet;
        private final Map<K, Instant> last = new HashMap<>();

        Cooldown(Duration quiet) {
            this.quiet = quiet;
        }

        /** @return true (and starts the cooldown) if the key may report now */
        boolean tryReport(K key, Instant now) {
            Instant before = last.get(key);
            if (before != null && Duration.between(before, now).compareTo(quiet) < 0) {
                return false;
            }
            last.put(key, now);
            return true;
        }
    }

    /** Key for per-trader-per-stock state. */
    record TraderSymbol(String traderId, String symbol) {
    }
}
