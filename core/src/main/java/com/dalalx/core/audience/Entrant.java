package com.dalalx.core.audience;

/**
 * Someone who appears on the leaderboard.
 *
 * @param traderId the id on their trades
 * @param name     display name
 * @param kind     what sort of trader they are
 */
public record Entrant(String traderId, String name, Kind kind) {

    /** The three sorts of trader on the floor. */
    public enum Kind { PRESENTER, AUDIENCE, BOT }
}
