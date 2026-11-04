package com.dalalx.core.bots;

import java.util.List;

/**
 * The cast of the simulated market, in one place (a simple factory).
 * Adding a new bot means writing one class and adding it here; the runner,
 * server and UI need no changes (Open/Closed).
 */
public final class StandardBots {

    private StandardBots() {
    }

    /** @return fresh instances of the four regular traders and The Spoofer */
    public static List<TradingBot> create() {
        return List.of(new MarketMakerBot(), new MomentumBot(), new PanicSellerBot(), new WhaleBot(), new SpooferBot());
    }
}
