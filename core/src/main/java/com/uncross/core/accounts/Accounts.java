package com.uncross.core.accounts;

import com.uncross.core.journal.Journal;
import com.uncross.core.journal.JournalEntry.AccountOpened;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everyone who has signed in to trade with their own money: opening
 * accounts, signing in with a PIN, and finding an account again from the
 * token the browser keeps.
 *
 * <p>Every account starts with {@link #STARTING_CASH} and no shares. Its
 * trader id is {@code A-} plus the name in capitals, which can never clash
 * with a bot ({@code MUNIM}) or an audience guest ({@code GUEST-…}).
 *
 * <p>Opening an account is written to the {@link Journal}, so accounts come
 * back after a restart along with everything they did.
 */
public final class Accounts {

    /** ₹10,00,000 (ten lakh rupees), in paise. */
    public static final long STARTING_CASH = 10_00_000_00L;
    public static final String PREFIX = "A-";
    public static final int MAX_ACCOUNTS = 200;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Account> byTrader = new ConcurrentHashMap<>();
    private final Map<String, String> sessions = new ConcurrentHashMap<>(); // token -> trader id
    private final Clock clock;
    private volatile Journal journal = Journal.NONE;

    public Accounts(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void journalTo(Journal newJournal) {
        this.journal = Objects.requireNonNull(newJournal, "journal must not be null");
    }

    /** The trader id an account called {@code name} trades under. */
    public static String traderIdFor(String name) {
        return PREFIX + name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static boolean isAccount(String traderId) {
        return traderId != null && traderId.startsWith(PREFIX);
    }

    /** Opens a new account and signs it in; returns the session token. */
    public synchronized String open(String name, String pin) {
        String clean = name == null ? "" : name.strip();
        if (!clean.matches("[\\p{L}0-9 ]{2,20}") || traderIdFor(clean).length() < PREFIX.length() + 2) {
            throw new IllegalArgumentException("a name is 2–20 letters, digits or spaces");
        }
        Pins.requireValid(pin);
        if (byTrader.containsKey(traderIdFor(clean))) {
            throw new IllegalArgumentException("there is already an account called " + clean);
        }
        if (byTrader.size() >= MAX_ACCOUNTS) {
            throw new IllegalArgumentException("the exchange has no room for more accounts");
        }
        Account account = create(clean, Pins.hash(pin));
        journal.record(new AccountOpened(clock.instant(), account.name(), account.pinHash()));
        return newSession(account);
    }

    /** Puts back an account found in the journal (no new session, nothing re-journalled). */
    public synchronized void restore(AccountOpened opened) {
        if (!byTrader.containsKey(traderIdFor(opened.name()))) {
            create(opened.name(), opened.pinHash());
        }
    }

    private Account create(String name, String pinHash) {
        Account account = new Account(name, traderIdFor(name), pinHash, STARTING_CASH);
        byTrader.put(account.traderId(), account);
        return account;
    }

    /** Signs in; returns a fresh session token. */
    public String signIn(String name, String pin) {
        Account account = name == null ? null : byTrader.get(traderIdFor(name.strip()));
        if (account == null || !account.pinMatches(pin)) {
            throw new IllegalArgumentException("that name and PIN do not match an account");
        }
        return newSession(account);
    }

    public void signOut(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private String newSession(Account account) {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        sessions.put(token, account.traderId());
        return token;
    }

    public Optional<Account> bySession(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(sessions.get(token)).map(byTrader::get);
    }

    public Optional<Account> byTrader(String traderId) {
        return traderId == null ? Optional.empty() : Optional.ofNullable(byTrader.get(traderId));
    }

    public List<Account> all() {
        return List.copyOf(byTrader.values());
    }
}
