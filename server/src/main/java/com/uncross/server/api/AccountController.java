package com.uncross.server.api;

import com.uncross.core.accounts.Account;
import com.uncross.core.accounts.Accounts;
import com.uncross.core.accounts.RiskDesk;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Signing in to trade with your own ₹10,00,000: open an account, sign in and
 * out, and read your statement.
 *
 * <p>A successful sign-in returns a token; the browser sends it back in the
 * {@code X-Uncross-Session} header, and the order controller uses it to place
 * orders from that account.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    public record Credentials(String name, String pin) {
    }

    /** One holding, valued at the last price. */
    public record HoldingLine(String symbol, long shares, long setAside, long lastPrice, long value) {
    }

    /** Your statement: cash, what is set aside, what you hold, and what it is all worth. */
    public record Statement(String name, String traderId, long cash, long cashSetAside, long availableCash,
                            List<HoldingLine> holdings, long worth, long profit) {
    }

    public record Session(String token, Statement account) {
    }

    private final Accounts accounts;
    private final RiskDesk risk;

    public AccountController(Accounts accounts, RiskDesk risk) {
        this.accounts = accounts;
        this.risk = risk;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Session open(@RequestBody Credentials body) {
        String token = accounts.open(body.name(), body.pin());
        return new Session(token, statement(token));
    }

    @PostMapping("/session")
    public Session signIn(@RequestBody Credentials body) {
        String token = accounts.signIn(body.name(), body.pin());
        return new Session(token, statement(token));
    }

    @DeleteMapping("/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void signOut(@RequestHeader(value = OrderController.SESSION_HEADER, required = false) String token) {
        accounts.signOut(token);
    }

    @GetMapping("/me")
    public Statement me(@RequestHeader(value = OrderController.SESSION_HEADER, required = false) String token) {
        return statement(token);
    }

    private Statement statement(String token) {
        Account account = accounts.bySession(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "please sign in again"));
        Account.Statement s = account.statement();
        List<HoldingLine> lines = s.holdings().stream().map(h -> {
            long last = risk.lastPrice(h.symbol());
            return new HoldingLine(h.symbol(), h.shares(), h.setAside(), last, last * h.shares());
        }).toList();
        long worth = s.cash() + lines.stream().mapToLong(HoldingLine::value).sum();
        return new Statement(s.name(), s.traderId(), s.cash(), s.cashSetAside(), s.availableCash(), lines, worth,
                worth - Accounts.STARTING_CASH);
    }
}
