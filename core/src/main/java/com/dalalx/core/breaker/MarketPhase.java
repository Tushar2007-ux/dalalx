package com.dalalx.core.breaker;

/** The trading phases a stock can be in, as the screen shows them. */
public enum MarketPhase {
    /** Continuous trading: orders match as they arrive. */
    OPEN,
    /** Circuit breaker tripped: no new orders, no trades, resting orders stay. */
    HALTED,
    /** Call auction: orders are collected and matched together at one price (Step 13). */
    AUCTION
}
