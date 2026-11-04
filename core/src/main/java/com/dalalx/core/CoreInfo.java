package com.dalalx.core;

/**
 * Placeholder class that proves the {@code core} module compiles, is tested,
 * and can be used by the {@code server} module.
 *
 * <p>It exists only for the project skeleton and will be replaced as soon as the
 * real domain packages ({@code model}, {@code book}, {@code matching}, ...) arrive.
 * It is {@code final} with a private constructor because it is a pure utility
 * holder: there is no state, so there is nothing to instantiate.
 */
public final class CoreInfo {

    /** Human-readable name of this module. */
    public static final String NAME = "DalalX Core";

    private CoreInfo() {
        // Utility class: not meant to be instantiated.
    }

    /**
     * Returns the module name.
     *
     * @return the constant {@link #NAME}
     */
    public static String name() {
        return NAME;
    }
}
