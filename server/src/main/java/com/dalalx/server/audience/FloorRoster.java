package com.dalalx.server.audience;

import com.dalalx.core.audience.Entrant;
import com.dalalx.core.audience.Entrant.Kind;
import com.dalalx.core.audience.GuestDesk;
import com.dalalx.core.audience.Leaderboard;
import com.dalalx.core.audience.Standing;
import com.dalalx.core.bots.BotLedger;
import com.dalalx.core.bots.BotRunner;
import com.dalalx.server.api.OrderController;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Who is on the floor right now: the presenter, every audience guest and every bot.
 *
 * <p>An adapter: it gathers names from three core objects and asks the core
 * {@link Leaderboard} to rank them. Used by the REST controller and by the
 * bridge that pushes the leaderboard to screens.
 */
@Component
public class FloorRoster {

    /** The presenter's name on the leaderboard. */
    public static final String PRESENTER_NAME = "You";

    private final GuestDesk guests;
    private final BotRunner bots;
    private final BotLedger ledger;

    public FloorRoster(GuestDesk guests, BotRunner bots, BotLedger ledger) {
        this.guests = guests;
        this.bots = bots;
        this.ledger = ledger;
    }

    /** @return everyone who can appear on the leaderboard */
    public List<Entrant> entrants() {
        List<Entrant> all = new ArrayList<>();
        all.add(new Entrant(OrderController.HUMAN, PRESENTER_NAME, Kind.PRESENTER));
        guests.guests().forEach(g -> all.add(new Entrant(g.traderId(), g.name(), Kind.AUDIENCE)));
        bots.bots().forEach(b -> all.add(new Entrant(b.id(), b.name(), Kind.BOT)));
        return all;
    }

    /** @return everyone ranked by profit, best first */
    public List<Standing> leaderboard() {
        return Leaderboard.rank(entrants(), ledger);
    }

    /** @return how many guests have joined */
    public int guestCount() {
        return guests.guests().size();
    }
}
