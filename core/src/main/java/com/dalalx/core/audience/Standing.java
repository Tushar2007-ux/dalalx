package com.dalalx.core.audience;

/**
 * One row of the leaderboard.
 *
 * @param rank     1 = most profitable
 * @param traderId the id on their trades
 * @param name     display name
 * @param kind     presenter, audience guest or bot
 * @param pnl      profit and loss in paise (mark-to-market)
 * @param trades   trades they took part in
 * @param volume   shares bought plus sold
 */
public record Standing(int rank, String traderId, String name, Entrant.Kind kind, long pnl, long trades, long volume) {
}
