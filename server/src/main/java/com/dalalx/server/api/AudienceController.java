package com.dalalx.server.api;

import com.dalalx.core.audience.Guest;
import com.dalalx.core.audience.GuestDesk;
import com.dalalx.core.audience.Standing;
import com.dalalx.core.bots.BotLedger;
import com.dalalx.core.bots.BotStats;
import com.dalalx.core.bots.Holding;
import com.dalalx.core.exchange.Exchange;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import com.dalalx.server.api.Dtos.OrderResult;
import com.dalalx.server.audience.FloorRoster;
import com.dalalx.server.audience.LanAddresses;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Audience trading from phones: join with a name, place orders, see your
 * own score, and the leaderboard for everyone.
 *
 * <p>An adapter, like every controller: the rules (name checks, order limits,
 * ranking) live in core's {@link GuestDesk} and {@code Leaderboard}. Orders
 * go through the same {@link Exchange} as everyone else's, so a guest's buy
 * really trades against the bots on the worker thread.
 *
 * <p><b>Who is who:</b> joining returns a secret token. The phone sends it in
 * the {@code X-Guest-Token} header with every order, so nobody can trade as
 * someone else just by knowing their (public) trader id.
 */
@RestController
@RequestMapping("/api")
public class AudienceController {

    /** Body of POST /api/audience/join. */
    public record Join(String name) {
    }

    /** The answer to joining. Keep {@code token} secret; it authorises orders. */
    public record Joined(String traderId, String name, String token) {
    }

    /**
     * Body of POST /api/audience/orders: MARKET (no price) or LIMIT (price in paise).
     */
    public record GuestOrder(String symbol, Side side, OrderType type, long price, long qty) {
    }

    /** A guest's own scorecard, for their phone. */
    public record GuestView(String traderId, String name, BotStats stats, List<Holding> holdings, int rank,
                            int of) {
    }

    /** Where phones can reach this computer, for the QR code. */
    public record Invite(List<String> addresses, int guests) {
    }

    /** The leaderboard plus how many guests have joined. */
    public record Board(List<Standing> standings, int guests) {
    }

    private final GuestDesk desk;
    private final Exchange exchange;
    private final BotLedger ledger;
    private final FloorRoster roster;

    public AudienceController(GuestDesk desk, Exchange exchange, BotLedger ledger, FloorRoster roster) {
        this.desk = desk;
        this.exchange = exchange;
        this.ledger = ledger;
        this.roster = roster;
    }

    /** {@code POST /api/audience/join}: sign in with a name (400 if the name is no good). */
    @PostMapping("/audience/join")
    @ResponseStatus(HttpStatus.CREATED)
    public Joined join(@RequestBody Join body) {
        Guest guest = desk.join(body.name());
        return new Joined(guest.traderId(), guest.name(), guest.token());
    }

    /** {@code POST /api/audience/orders}: trade as the guest who owns the token. */
    @PostMapping("/audience/orders")
    public OrderResult order(@RequestHeader(value = "X-Guest-Token", required = false) String token,
                             @RequestBody GuestOrder body) {
        Guest guest = guest(token);
        String refusal = desk.refusal(guest, body.qty());
        if (refusal != null) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, refusal);
        }
        OrderType type = body.type() == null ? OrderType.MARKET : body.type();
        OrderRequest request = new OrderRequest(body.symbol(), guest.traderId(), body.side(), type,
                type == OrderType.MARKET ? 0 : body.price(), body.qty());
        return OrderResult.of(Futures.await(exchange.submit(request)));
    }

    /** {@code POST /api/audience/cancel-all}: withdraw every resting order of this guest. */
    @PostMapping("/audience/cancel-all")
    public int cancelAll(@RequestHeader(value = "X-Guest-Token", required = false) String token) {
        return Futures.await(exchange.cancelAllFor(guest(token).traderId()));
    }

    /** {@code GET /api/audience/me}: this guest's score, holdings and rank. */
    @GetMapping("/audience/me")
    public GuestView me(@RequestHeader(value = "X-Guest-Token", required = false) String token) {
        Guest guest = guest(token);
        List<Standing> board = roster.leaderboard();
        int rank = board.stream().filter(s -> s.traderId().equals(guest.traderId()))
                .mapToInt(Standing::rank).findFirst().orElse(board.size());
        return new GuestView(guest.traderId(), guest.name(), ledger.stats(guest.traderId()),
                ledger.holdings(guest.traderId()), rank, board.size());
    }

    /** {@code GET /api/audience/invite}: this computer's network addresses for the QR code. */
    @GetMapping("/audience/invite")
    public Invite invite() {
        return new Invite(LanAddresses.find(), roster.guestCount());
    }

    /** {@code GET /api/leaderboard}: presenter, guests and bots ranked by profit. */
    @GetMapping("/leaderboard")
    public Board leaderboard() {
        return new Board(roster.leaderboard(), roster.guestCount());
    }

    private Guest guest(String token) {
        return desk.byToken(token).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please join first"));
    }
}
