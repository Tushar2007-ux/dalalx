package com.dalalx.core.bots;

import com.dalalx.core.exchange.Exchange;
import com.dalalx.core.matching.CancelResult;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.model.OrderRequest;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The real {@link OrderGateway}: queues the order on the stock's worker thread
 * and blocks the bot thread until the answer comes back (an Adapter from the
 * exchange's asynchronous API to the simple blocking one bots want).
 *
 * <p>Blocking is fine here because bots run on their own thread, never on a
 * worker; a worker waiting on itself would deadlock.
 */
public final class ExchangeGateway implements OrderGateway {

    private static final long TIMEOUT_SECONDS = 5;

    private final Exchange exchange;

    public ExchangeGateway(Exchange exchange) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
    }

    @Override
    public MatchResult submit(OrderRequest request) {
        return await(exchange.submit(request));
    }

    @Override
    public CancelResult cancel(long orderId) {
        return await(exchange.cancel(orderId));
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the exchange", e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("exchange did not answer in " + TIMEOUT_SECONDS + "s", e);
        }
    }
}
