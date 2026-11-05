package com.dalalx.core.bots;

import com.dalalx.core.breaker.AuctionState;
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
    private final String name;
    private final String role;
    private final String line;
    private final int everyTicks;
    /**
     * The only field another thread writes (a web request turning a dial).
     * {@code volatile} makes the new record visible to the bot thread at once;
     * the record is immutable, so it can never be seen half-changed.
     */
    private volatile BotTuning tuning = BotTuning.NORMAL;
    /** Orders of ours that rested in a book: id → symbol. */
    private final Map<Long, String> resting = new LinkedHashMap<>();

    protected AbstractBot(String id, String name, String role, String line, int everyTicks) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.line = Objects.requireNonNull(line, "line must not be null");
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
    public final String name() {
        return name;
    }

    @Override
    public final String role() {
        return role;
    }

    @Override
    public final String line() {
        return line;
    }

    @Override
    public final BotTuning tuning() {
        return tuning;
    }

    @Override
    public final void tune(BotTuning newTuning) {
        this.tuning = Objects.requireNonNull(newTuning, "tuning must not be null");
    }

    @Override
    public final int everyTicks() {
        return everyTicks;
    }

    /** Sends a limit order (size scaled by the size dial) and remembers it if part of it rests. */
    protected final MatchResult limit(BotContext ctx, String symbol, Side side, long price, long qty) {
        MatchResult result = ctx.orders().submit(
                new OrderRequest(symbol, id, side, OrderType.LIMIT, Ticks.round(price), tuning.scale(qty)));
        if (result.rested()) {
            resting.put(result.incoming().id(), symbol);
        }
        return result;
    }

    /**
     * Sends an immediate-or-cancel order: trades up to {@code price}, never rests.
     * During a call auction (which only takes limit orders) the bot sends the
     * same order as a limit order instead, so it joins the auction like a
     * real trader would, and remembers it so it can be withdrawn later.
     */
    protected final MatchResult ioc(BotContext ctx, String symbol, Side side, long price, long qty) {
        MatchResult result = ctx.orders().submit(
                new OrderRequest(symbol, id, side, OrderType.IOC, Ticks.round(price), tuning.scale(qty)));
        if (AuctionState.LIMIT_ONLY.equals(result.reason())) {
            return limit(ctx, symbol, side, price, qty);
        }
        return result;
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
