package com.dalalx.core.bots;

import java.util.random.RandomGenerator;

/**
 * The two dials on a correspondent's card: how often it trades and how big.
 * Both run from 0 to 4; 2 is the bot's normal behaviour.
 *
 * <ul>
 *   <li><b>aggressiveness</b> — 0 acts on a third of its turns, 1 on two
 *       thirds, 2 every turn, 3 every turn plus half the time again, 4 twice
 *       per turn.</li>
 *   <li><b>size</b> — scales every order's quantity by ¼, ½, 1, 2 or 4.</li>
 * </ul>
 * Immutable: changing a dial makes a new {@code BotTuning}, which the bot
 * swaps in atomically (see {@link AbstractBot#tune}).
 *
 * @param aggressiveness 0–4
 * @param size           0–4
 */
public record BotTuning(int aggressiveness, int size) {

    public static final BotTuning NORMAL = new BotTuning(2, 2);
    public static final int MAX = 4;

    /** Quantity multipliers in quarters: ¼, ½, 1, 2, 4. */
    private static final long[] SIZE_QUARTERS = {1, 2, 4, 8, 16};

    public BotTuning {
        if (aggressiveness < 0 || aggressiveness > MAX || size < 0 || size > MAX) {
            throw new IllegalArgumentException("dials run from 0 to " + MAX);
        }
    }

    /** @return how many times the bot should act this turn */
    public int actsThisTurn(RandomGenerator random) {
        return switch (aggressiveness) {
            case 0 -> random.nextInt(3) == 0 ? 1 : 0;
            case 1 -> random.nextInt(3) < 2 ? 1 : 0;
            case 2 -> 1;
            case 3 -> random.nextBoolean() ? 2 : 1;
            default -> 2;
        };
    }

    /** @return {@code qty} scaled by the size dial, never below 1 */
    public long scale(long qty) {
        return Math.max(1, qty * SIZE_QUARTERS[size] / 4);
    }
}
