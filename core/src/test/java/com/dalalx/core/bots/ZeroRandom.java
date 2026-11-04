package com.dalalx.core.bots;

import java.util.random.RandomGenerator;

/**
 * Dice that always roll the lowest value: every {@code chance()} succeeds,
 * {@code pick()} takes the first item, quantities are the minimum and
 * {@code nextBoolean()} is false. Makes bot decisions fully predictable.
 */
final class ZeroRandom implements RandomGenerator {

    @Override
    public long nextLong() {
        return 0;
    }
}
