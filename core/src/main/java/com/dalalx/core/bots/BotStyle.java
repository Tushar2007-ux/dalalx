package com.dalalx.core.bots;

import java.util.Locale;
import java.util.function.BiFunction;

/**
 * The trading styles a new correspondent can be hired with. Each constant
 * knows how to build its bot (a tiny Factory per enum constant), so hiring
 * never needs a {@code switch} and a new style is one new line here.
 */
public enum BotStyle {
    CONTRARIAN(ContrarianBot::new),
    NOISE(NoiseBot::new),
    MARKET_MAKER((id, name) -> new MarketMakerBot(id, name, "Quotes a second set of prices beside The Munim.")),
    MOMENTUM((id, name) -> new MomentumBot(id, name, "Follows the trend, a little later than everyone else."));

    private final BiFunction<String, String, TradingBot> maker;

    BotStyle(BiFunction<String, String, TradingBot> maker) {
        this.maker = maker;
    }

    /**
     * Builds a bot of this style. Its trader id is the name in capitals with
     * anything but letters and digits removed: "The Contrarian" → "THECONTRARIAN".
     *
     * @throws IllegalArgumentException if the name has no letters or digits
     */
    public TradingBot hire(String name) {
        String clean = name == null ? "" : name.strip();
        String id = clean.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (id.isEmpty() || clean.length() > 40) {
            throw new IllegalArgumentException("a correspondent needs a name of 1–40 characters with letters or digits");
        }
        return maker.apply(id, clean);
    }
}
