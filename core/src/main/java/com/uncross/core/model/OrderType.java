package com.uncross.core.model;

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
 *   <li>{@link #STOP_LOSS}: waits, unseen, until the last traded price reaches
 *       its <em>trigger</em> price, then sells (or buys) at the market. Its
 *       {@code price} is the trigger, not a limit.</li>
 *   <li>{@link #ICEBERG}: a limit order that shows only a slice of its size in
 *       the book. When a slice is used up, the next one appears at the back of
 *       the queue, so the hidden part never jumps the line.</li>
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
    IOC(true),
    STOP_LOSS(true),
    ICEBERG(true);

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
