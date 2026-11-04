package com.dalalx.server.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Waits for an answer from an exchange worker, for the web layer.
 *
 * <p>Exchange calls return futures because the work happens on the symbol's
 * worker thread. A web request needs an answer, so it waits, but never forever:
 * after a few seconds it gives up rather than tying up a web thread. If the
 * worker threw an exception, the <em>original</em> exception is rethrown, so
 * the error handler can turn it into the right HTTP status.
 */
final class Futures {

    private static final long TIMEOUT_SECONDS = 5;

    private Futures() {
    }

    static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException | CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("exchange did not answer within " + TIMEOUT_SECONDS + " s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the exchange", e);
        }
    }
}
