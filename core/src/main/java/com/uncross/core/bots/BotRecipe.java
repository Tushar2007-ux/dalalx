package com.uncross.core.bots;

import com.uncross.core.model.Side;
import java.util.Locale;
import java.util.Objects;

/**
 * The rules for a bot that a reader builds from a menu: "when this happens
 * to this stock, buy (or sell) this many shares".
 *
 * <p>The recipe is just data. {@link RuleBot} follows it, and the
 * {@link When} part is itself a small Strategy, so a new kind of trigger is a
 * new enum constant with its own rule, not an edit to the bot.
 *
 * @param name      what the bot is called (1–40 characters)
 * @param symbol    the stock it watches and trades
 * @param when      what sets it off
 * @param amount    for FALLS/RISES, the move in basis points (100 = 1%); for EVERY, seconds
 * @param side      BUY or SELL
 * @param qty       shares per trade
 * @param maxShares the most it may hold, long or short, so a runaway rule stops itself
 */
public record BotRecipe(String name, String symbol, When when, long amount, Side side, long qty, long maxShares) {

    /** What sets a rule bot off. Each constant carries its own test: the Strategy pattern, in an enum. */
    public enum When {
        /** The price has fallen {@code amount} basis points since the bot last acted. */
        FALLS {
            @Override
            boolean fires(long anchor, long last, int ticksSince, long amount, int ticksPerSecond) {
                return last <= Ticks.moveBy(anchor, -amount);
            }

            @Override
            String describe(long amount) {
                return "whenever the price falls " + percent(amount);
            }
        },
        /** The price has risen {@code amount} basis points since the bot last acted. */
        RISES {
            @Override
            boolean fires(long anchor, long last, int ticksSince, long amount, int ticksPerSecond) {
                return last >= Ticks.moveBy(anchor, amount);
            }

            @Override
            String describe(long amount) {
                return "whenever the price rises " + percent(amount);
            }
        },
        /** Every {@code amount} seconds, whatever the price does. */
        EVERY {
            @Override
            boolean fires(long anchor, long last, int ticksSince, long amount, int ticksPerSecond) {
                return ticksSince >= amount * ticksPerSecond;
            }

            @Override
            String describe(long amount) {
                return "every " + amount + (amount == 1 ? " second" : " seconds");
            }
        };

        abstract boolean fires(long anchor, long last, int ticksSince, long amount, int ticksPerSecond);

        abstract String describe(long amount);

        private static String percent(long bp) {
            return String.format(Locale.ROOT, "%d.%02d%%", bp / 100, bp % 100);
        }
    }

    public static final long MIN_MOVE_BP = 10;
    public static final long MAX_MOVE_BP = 2_000;
    public static final long MAX_SECONDS = 300;
    public static final long MAX_QTY = 5_000;
    public static final long MAX_HOLDING = 50_000;

    public BotRecipe {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > 40 || idFor(clean).isEmpty()) {
            throw new IllegalArgumentException("a bot needs a name of 1–40 characters with letters or digits");
        }
        name = clean;
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(when, "when must not be null");
        Objects.requireNonNull(side, "side must not be null");
        if (when == When.EVERY ? amount < 1 || amount > MAX_SECONDS : amount < MIN_MOVE_BP || amount > MAX_MOVE_BP) {
            throw new IllegalArgumentException(when == When.EVERY
                    ? "repeat every 1–" + MAX_SECONDS + " seconds"
                    : "a move of 0.10%–20.00% (10–2000 basis points)");
        }
        if (qty < 1 || qty > MAX_QTY) {
            throw new IllegalArgumentException("trade 1–" + MAX_QTY + " shares at a time");
        }
        if (maxShares < qty || maxShares > MAX_HOLDING) {
            throw new IllegalArgumentException("the holding limit must be between the trade size and " + MAX_HOLDING);
        }
    }

    /** The bot's trader id: its name in capitals, letters and digits only (as for every hired bot). */
    public String id() {
        return idFor(name);
    }

    static String idFor(String name) {
        return name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** The rule in one plain sentence, e.g. "Buys 200 TCS whenever the price falls 1.50%". */
    public String describe() {
        return (side == Side.BUY ? "Buys " : "Sells ") + qty + " " + symbol + " " + when.describe(amount)
                + ", holding at most " + maxShares + ".";
    }
}
