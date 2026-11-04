package com.dalalx.core.model;

/**
 * How an order should be executed.
 *
 * <ul>
 *   <li>{@link #LIMIT}: trade at the limit price or better; any unfilled rest
 *       waits in the order book.</li>
 *   <li>{@link #MARKET}: trade immediately at the best available prices; has no
 *       price of its own; never rests in the book.</li>
 *   <li>{@link #IOC} (Immediate-Or-Cancel): like LIMIT, but whatever can't be
 *       filled immediately is cancelled instead of resting.</li>
 * </ul>
 *
 * <p>This enum only <em>names</em> the types. How each type matches will be a
 * separate Strategy object in the {@code matching} package. That keeps the model
 * Open/Closed: adding a new order type means adding a constant and a strategy,
 * without rewriting the existing ones.
 */
public enum OrderType {
    LIMIT(true),
    MARKET(false),
    IOC(true);

    private final boolean requiresLimitPrice;

    OrderType(boolean requiresLimitPrice) {
        this.requiresLimitPrice = requiresLimitPrice;
    }

    /**
     * Tells whether orders of this type must carry a positive limit price.
     *
     * @return {@code true} for LIMIT and IOC, {@code false} for MARKET
     */
    public boolean requiresLimitPrice() {
        return requiresLimitPrice;
    }
}
