package com.dalalx.core.audience;

/**
 * Someone in the audience who joined from their phone.
 *
 * @param traderId the id on their orders and trades, e.g. {@code GUEST-7}
 * @param name     the name they typed, shown on the tape and leaderboard
 * @param token    a secret only their phone knows; it proves an order is really theirs
 */
public record Guest(String traderId, String name, String token) {

    /** Every guest's trader id starts with this, so other parts can recognise them. */
    public static final String PREFIX = "GUEST-";

    /** @return {@code true} if {@code traderId} belongs to an audience guest */
    public static boolean isGuest(String traderId) {
        return traderId != null && traderId.startsWith(PREFIX);
    }
}
