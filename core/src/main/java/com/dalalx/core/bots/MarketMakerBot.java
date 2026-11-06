package com.dalalx.core.bots;

import com.dalalx.core.model.Side;
import java.util.List;

/**
 * <b>The Munim</b> — the market maker. It keeps the book full by always quoting
 * both sides: a few bids just below a fair price and a few asks just above it.
 * It earns the spread when others trade against it, and gives everyone else
 * something to trade with.
 *
 * <p>Each turn it re-quotes one stock (round robin): cancel its old quotes
 * there, work out a fair price, then place {@link #LEVELS} levels per side.
 * The fair price is the last trade pulled 1% of the way back toward the
 * reference price, a gentle mean reversion that stops the simulated market
 * from drifting off forever.
 *
 * <p>Its P&amp;L is often negative: momentum and panic traders only trade with
 * it when they know the price is about to move ("adverse selection"), so
 * its quotes get picked off. Real market makers fight this with wider
 * spreads and faster re-quoting; here it is part of the story.
 */
public final class MarketMakerBot extends AbstractBot {

    public static final String ID = "MUNIM";

    static final int LEVELS = 5;
    /** Half the quoted spread, in basis points (0.05%). */
    static final long HALF_SPREAD_BP = 5;
    /** Gap between quote levels, in basis points. */
    static final long STEP_BP = 4;
    /**
     * Mean-reversion strength: the fair price moves 1/100th of the way back to
     * the reference. Gentle enough that a determined seller can still crash
     * the price (and trip the circuit breaker), strong enough to stop drift.
     */
    static final long REVERSION = 100;
    /** Quote sizes: deep enough that one trader's burst doesn't empty a level at once. */
    static final long MIN_QTY = 150;
    static final long MAX_QTY = 600;

    private int next;

    public MarketMakerBot() {
        this(ID, "The Munim", "Quotes both sides all session and keeps the spread honest.");
    }

    /** A second market maker hired from the Correspondents page. */
    public MarketMakerBot(String id, String name, String line) {
        super(id, name, "Market Maker", line, 1);
    }

    @Override
    public void act(BotContext ctx) {
        List<String> symbols = ctx.market().symbols();
        String symbol = symbols.get(next++ % symbols.size());
        withdraw(ctx.orders(), symbol);
        long fair = fairPrice(ctx.market(), symbol);
        for (int level = 0; level < LEVELS; level++) {
            long offset = HALF_SPREAD_BP + level * STEP_BP;
            limit(ctx, symbol, Side.BUY, Ticks.moveBy(fair, -offset), ctx.qty(MIN_QTY, MAX_QTY, 10));
            limit(ctx, symbol, Side.SELL, Ticks.moveBy(fair, offset), ctx.qty(MIN_QTY, MAX_QTY, 10));
        }
    }

    /** Last price, nudged 1/{@value #REVERSION}th of the way back toward the reference. */
    static long fairPrice(MarketView market, String symbol) {
        long last = market.lastPrice(symbol);
        return Ticks.round(last + (market.referencePrice(symbol) - last) / REVERSION);
    }
}
