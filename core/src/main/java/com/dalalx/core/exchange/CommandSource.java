package com.dalalx.core.exchange;

/**
 * Where a command for a worker came from. Used only for monitoring (the
 * Engine Room shows each source as a different colour of ticket); it never
 * changes how a command is matched.
 */
public enum CommandSource {
    /** The person at the main screen. */
    PRESENTER,
    /** Someone in the audience trading from their phone. */
    AUDIENCE,
    /** One of the simulated traders. */
    BOT,
    /** The exchange clock's regular tick (ends halts and auctions on time). */
    CLOCK,
    /** Everything else: book snapshots, surveillance cancels, market views. */
    SYSTEM
}
