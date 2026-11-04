package com.dalalx.core.bots;

import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The parts every bot shares: its id and role, and the bookkeeping of which of
 * its orders are still resting so it can cancel them later.
 *
 * <p>Subclasses decide <em>what</em> to trade ({@link #act}); this class
 * handles <em>how</em> orders are sent and remembered (inheritance for shared
 * behaviour, polymorphism for the differences).
 *
 * <p><b>Thread confinement:</b> a bot is only ever used by the single bot
 * thread, so its fields need no locks.
 */
public abstract class AbstractBot implements TradingBot {

    private final String id;
    private final String role;
    private final int everyTicks;
    /** Orders of ours that rested in a book: id → symbol. */
    private final Map<Long, String> resting = new LinkedHashMap<>();

    protected AbstractBot(String id, String role, int everyTicks) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        if (everyTicks < 1) {
            throw new IllegalArgumentException("everyTicks must be >= 1");
        }
        this.everyTicks = everyTicks;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String role() {
        return role;
    }

    @Override
    public final int everyTicks() {
        return everyTicks;
    }

    /** Sends a limit order and remembers it if part of it rests in the book. */
    protected final MatchResult limit(BotContext ctx, String symbol, Side side, long price, long qty) {
        MatchResult result = ctx.orders().submit(new OrderRequest(symbol, id, side, OrderType.LIMIT, Ticks.round(price), qty));
        if (result.rested()) {
            resting.put(result.incoming().id(), symbol);
        }
        return result;
    }

    /** Sends an immediate-or-cancel order: trades up to {@code price}, never rests. */
    protected final MatchResult ioc(BotContext ctx, String symbol, Side side, long price, long qty) {
        return ctx.orders().submit(new OrderRequest(symbol, id, side, OrderType.IOC, Ticks.round(price), qty));
    }

    @Override
    public final void withdraw(OrderGateway orders) {
        resting.keySet().forEach(orders::cancel); // already-filled ones just answer "already FILLED"
        resting.clear();
    }

    /** Cancels this bot's resting orders in one symbol only. */
    protected final void withdraw(OrderGateway orders, String symbol) {
        List<Long> ids = resting.entrySet().stream()
                .filter(e -> e.getValue().equals(symbol)).map(Map.Entry::getKey).toList();
        ids.forEach(orders::cancel);
        ids.forEach(resting::remove);
    }

    /** @return how many orders this bot believes are still resting */
    public final int restingCount() {
        return resting.size();
    }
}
