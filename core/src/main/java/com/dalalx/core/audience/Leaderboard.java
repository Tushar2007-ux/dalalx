package com.dalalx.core.audience;

import com.dalalx.core.bots.BotLedger;
import com.dalalx.core.bots.BotStats;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks everyone on the floor by profit: the presenter, the audience and the bots.
 *
 * <p>The numbers come from the {@link BotLedger}, which already keeps score
 * for every trader id; this class only looks them up, sorts them and numbers
 * the rows. Ties are broken by more trades, then by name, so the order is
 * stable from one second to the next.
 */
public final class Leaderboard {

    private Leaderboard() {
    }

    /** @return one standing per entrant, best first */
    public static List<Standing> rank(List<Entrant> entrants, BotLedger ledger) {
        record Row(Entrant who, BotStats stats) {
        }
        List<Row> rows = new ArrayList<>();
        for (Entrant e : entrants) {
            rows.add(new Row(e, ledger.stats(e.traderId())));
        }
        rows.sort(Comparator.comparingLong((Row r) -> r.stats().pnl()).reversed()
                .thenComparing(Comparator.comparingLong((Row r) -> r.stats().trades()).reversed())
                .thenComparing(r -> r.who().name()));
        List<Standing> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            out.add(new Standing(i + 1, r.who().traderId(), r.who().name(), r.who().kind(),
                    r.stats().pnl(), r.stats().trades(), r.stats().volume()));
        }
        return out;
    }
}
