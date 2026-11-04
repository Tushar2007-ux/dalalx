package com.dalalx.core.exchange;

import com.dalalx.core.matching.SymbolEngine;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * One piece of work for a symbol's worker thread, plus the place to put its answer.
 *
 * <p><b>Command pattern:</b> a request ("submit this order", "cancel that one",
 * "show me the book") is turned into an object that can be queued and run later
 * by another thread. The caller gets the {@link CompletableFuture} straight
 * away and can wait on it, or attach a callback, while the worker gets round to
 * running the action.
 *
 * <p>This is how threads talk to an order book without ever touching it: they
 * don't call the {@link SymbolEngine} directly; they hand a command to its
 * worker, and only the worker runs it.
 *
 * @param name   short description, for logs and errors
 * @param action what to do with the engine, on the worker thread
 * @param result completed with the action's return value (or its exception)
 * @param <T>    the type of the answer
 */
record Command<T>(String name, Function<SymbolEngine, T> action, CompletableFuture<T> result) {

    Command {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(result, "result must not be null");
    }

    static <T> Command<T> of(String name, Function<SymbolEngine, T> action) {
        return new Command<>(name, action, new CompletableFuture<>());
    }

    /** Runs the action and delivers the outcome to whoever is waiting on {@link #result()}. */
    void execute(SymbolEngine engine) {
        try {
            result.complete(action.apply(engine));
        } catch (RuntimeException e) {
            result.completeExceptionally(e); // the caller sees the error; the worker keeps running
        }
    }
}
