package com.dalalx.core.model;

/**
 * The lifecycle state of an {@link Order}.
 *
 * <p>Legal transitions (everything else throws {@link IllegalStateException}):
 * <pre>
 *   from              action          to
 *   ----------------  --------------  ----------------
 *   NEW               fill (part)     PARTIALLY_FILLED
 *   NEW               fill (all)      FILLED
 *   NEW               cancel          CANCELLED
 *   NEW               reject          REJECTED
 *   PARTIALLY_FILLED  fill (part)     PARTIALLY_FILLED
 *   PARTIALLY_FILLED  fill (rest)     FILLED
 *   PARTIALLY_FILLED  cancel          CANCELLED
 * </pre>
 *
 * <p>FILLED, CANCELLED and REJECTED are <em>terminal</em>: once an order reaches
 * one of them, nothing more can happen to it. {@link Order} enforces these rules.
 * The project uses the full State pattern for market status, where behaviour
 * changes a lot from state to state. Here an enum plus guard checks is enough,
 * because the order's behaviour barely differs between states.
 */
public enum OrderStatus {
    NEW(false),
    PARTIALLY_FILLED(false),
    FILLED(true),
    CANCELLED(true),
    REJECTED(true);

    private final boolean terminal;

    OrderStatus(boolean terminal) {
        this.terminal = terminal;
    }

    /**
     * Tells whether this is a final state that no further action can leave.
     *
     * @return {@code true} for FILLED, CANCELLED and REJECTED
     */
    public boolean isTerminal() {
        return terminal;
    }
}
