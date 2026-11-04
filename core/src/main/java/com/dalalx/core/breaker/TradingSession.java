package com.dalalx.core.breaker;

import com.dalalx.core.model.Money;
import com.dalalx.core.model.Trade;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The State-pattern <em>context</em> for one stock: it holds the current
 * {@link MarketState}, feeds trades to the {@link CircuitBreaker}, and swaps
 * the state when the breaker trips or a phase runs out.
 *
 * <p>Owned by the stock's worker thread (it lives inside {@code SymbolEngine}),
 * so like the order book it needs no locks.
 */
public final class TradingSession {

    private final String symbol;
    private final CircuitBreaker breaker;
    private final Function<Instant, MarketState> reopen;
    private MarketState state = OpenState.INSTANCE;

    /**
     * @param symbol  the stock
     * @param breaker the breaker to consult, or {@code null} for none
     * @param reopen  what follows a halt (given the time the halt ends)
     */
    public TradingSession(String symbol, CircuitBreaker breaker, Function<Instant, MarketState> reopen) {
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.breaker = breaker;
        this.reopen = Objects.requireNonNull(reopen, "reopen must not be null");
    }

    /** A session that never halts (used by tests of pure matching). */
    public static TradingSession alwaysOpen(String symbol) {
        return new TradingSession(symbol, null, now -> OpenState.INSTANCE);
    }

    /** A session with the standard rule, reopening straight into continuous trading. */
    public static TradingSession withBreaker(String symbol, BreakerRule rule) {
        return new TradingSession(symbol, new CircuitBreaker(rule), now -> OpenState.INSTANCE);
    }

    /** A session with a breaker and a custom reopening (Step 13: a call auction). */
    public static TradingSession withBreaker(String symbol, BreakerRule rule, Function<Instant, MarketState> reopen) {
        return new TradingSession(symbol, new CircuitBreaker(rule), reopen);
    }

    public MarketState state() {
        return state;
    }

    public MarketPhase phase() {
        return state.phase();
    }

    /** @return why new orders are refused right now, if they are */
    public Optional<String> refusal() {
        return state.refusal();
    }

    /**
     * Lets time pass: a halt that has run out ends.
     *
     * @return the new state if it changed
     */
    public Optional<MarketState> onClock(Instant now) {
        MarketState next = state.onClock(now);
        if (next == state) {
            return Optional.empty();
        }
        if (state.phase() == MarketPhase.HALTED && breaker != null) {
            breaker.reset(); // a fresh minute after reopening; the old fall can't trip it again
        }
        state = next;
        return Optional.of(next);
    }

    /** Puts the session into a state chosen by someone else (e.g. the auction finishing). */
    public void enter(MarketState next) {
        state = Objects.requireNonNull(next, "next must not be null");
    }

    /**
     * Feeds one trade to the breaker while trading is open.
     *
     * @return the halted state if this trade tripped the breaker
     */
    public Optional<MarketState> onTrade(Trade trade) {
        if (breaker == null || state.phase() != MarketPhase.OPEN) {
            return Optional.empty();
        }
        CircuitBreaker.Trip trip = breaker.record(trade.timestamp(), trade.price());
        if (trip == null) {
            return Optional.empty();
        }
        state = new HaltedState(trade.timestamp().plus(breaker.rule().haltFor()), describe(trip), reopen);
        return Optional.of(state);
    }

    /** "RELIANCE fell 10.2% in 60 seconds (₹2,460.00 → ₹2,209.00)". */
    private String describe(CircuitBreaker.Trip trip) {
        long bp = Math.abs(trip.moveBp());
        return "%s %s %d.%d%% in %d seconds (%s → %s)".formatted(symbol, trip.isFall() ? "fell" : "rose",
                bp / 100, bp % 100 / 10, breaker.rule().window().toSeconds(),
                Money.format(trip.fromPrice()), Money.format(trip.toPrice()));
    }
}
