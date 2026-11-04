package com.dalalx.core.breaker;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The highest and lowest trade prices of the last {@code window} (e.g. 60 s),
 * each available in O(1).
 *
 * <p><b>Algorithm (monotonic deques, the "sliding window maximum" trick):</b>
 * the max-deque keeps prices in decreasing order. When a new price arrives,
 * every older price that is not higher is thrown away from the back: it can
 * never be the maximum again, because the new price is at least as high and
 * will stay in the window longer. The front is therefore always the maximum.
 * Entries that are too old are dropped from the front. The min-deque is the
 * mirror image. Each price is added and removed at most once, so the cost per
 * trade is O(1) amortised, however many trades are in the window.
 *
 * <p>Not thread-safe: it belongs to one symbol's worker thread.
 */
final class PriceWindow {

    private record Point(Instant time, long price) {
    }

    private final Duration window;
    private final Deque<Point> max = new ArrayDeque<>();
    private final Deque<Point> min = new ArrayDeque<>();

    PriceWindow(Duration window) {
        this.window = window;
    }

    /** Records a trade and forgets anything older than the window. */
    void add(Instant time, long price) {
        Point p = new Point(time, price);
        while (!max.isEmpty() && max.peekLast().price() <= price) {
            max.removeLast();
        }
        max.addLast(p);
        while (!min.isEmpty() && min.peekLast().price() >= price) {
            min.removeLast();
        }
        min.addLast(p);
        Instant cutoff = time.minus(window);
        while (max.peekFirst().time().isBefore(cutoff)) {
            max.removeFirst();
        }
        while (min.peekFirst().time().isBefore(cutoff)) {
            min.removeFirst();
        }
    }

    long highest() {
        return max.peekFirst().price();
    }

    long lowest() {
        return min.peekFirst().price();
    }

    boolean isEmpty() {
        return max.isEmpty();
    }

    void clear() {
        max.clear();
        min.clear();
    }
}
