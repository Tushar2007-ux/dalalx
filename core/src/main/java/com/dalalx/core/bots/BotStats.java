package com.dalalx.core.bots;

import java.util.Map;

/**
 * One trader's scorecard at a moment in time (an immutable value).
 *
 * @param traderId  whose numbers these are
 * @param orders    how many orders they sent today
 * @param trades    how many trades they were part of
 * @param volume    shares bought plus shares sold
 * @param cash      paise received minus paise paid (negative after buying)
 * @param positions shares held per symbol (negative = short)
 * @param pnl       profit and loss in paise: cash plus positions valued at the last price
 */
public record BotStats(String traderId, long orders, long trades, long volume, long cash, Map<String, Long> positions, long pnl) {

    public BotStats {
        positions = Map.copyOf(positions);
    }
}
