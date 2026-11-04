package com.dalalx.core.bots;

import com.dalalx.core.matching.CancelResult;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.matching.MatchingStrategies;
import com.dalalx.core.matching.OrderFactory;
import com.dalalx.core.matching.SymbolEngine;
import com.dalalx.core.model.OrderRequest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A single-threaded gateway over real {@link SymbolEngine}s: bots' orders are
 * genuinely matched, and every request and cancel is recorded for assertions.
 */
final class EngineGateway implements OrderGateway {

    final List<OrderRequest> sent = new ArrayList<>();
    final List<Long> cancelled = new ArrayList<>();
    private final Map<String, SymbolEngine> engines = new HashMap<>();
    private final Map<Long, String> symbolOf = new HashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public MatchResult submit(OrderRequest request) {
        sent.add(request);
        MatchResult result = engine(request.symbol()).submit(request);
        symbolOf.put(result.incoming().id(), request.symbol());
        return result;
    }

    @Override
    public CancelResult cancel(long orderId) {
        cancelled.add(orderId);
        String symbol = symbolOf.get(orderId);
        return symbol == null ? CancelResult.unknown(orderId) : engine(symbol).cancel(orderId);
    }

    SymbolEngine engine(String symbol) {
        return engines.computeIfAbsent(symbol, s -> new SymbolEngine(s,
                new OrderFactory(ids::incrementAndGet, ids::incrementAndGet, Clock.systemUTC()),
                MatchingStrategies.standard(ids::incrementAndGet, Clock.systemUTC())));
    }
}
