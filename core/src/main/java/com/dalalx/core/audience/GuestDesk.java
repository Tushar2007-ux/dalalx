package com.dalalx.core.audience;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The front desk for audience trading: lets people join with a name, checks
 * who is behind each order, and keeps anyone from flooding the market.
 *
 * <p><b>Why limits?</b> During a live demo, thirty phones hammering "Buy" must
 * not drown the bots or freeze the screen. Each guest gets a small
 * <em>token bucket</em>: it holds {@value #BURST} order tokens and refills at
 * {@value #ORDERS_PER_SECOND} per second. Each order spends one; with none left,
 * the order is refused until the bucket refills. Orders are also capped at
 * {@value #MAX_QTY} shares.
 *
 * <p><b>Thread safety:</b> many web threads call this at once, so every public
 * method is {@code synchronized}. The work inside is a few map lookups.
 */
public final class GuestDesk {

    /** Most guests at once (a big lecture hall). */
    public static final int MAX_GUESTS = 300;
    /** Largest order a guest may send, in shares. */
    public static final long MAX_QTY = 1_000;
    /** Steady order rate allowed per guest. */
    public static final int ORDERS_PER_SECOND = 3;
    /** Orders a guest may send in a quick burst. */
    public static final int BURST = 6;

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} ._'-]{2,20}");

    private final Map<String, Guest> byToken = new LinkedHashMap<>();
    private final Map<String, Guest> byTrader = new LinkedHashMap<>();
    private final Map<String, Bucket> buckets = new LinkedHashMap<>();
    private final Set<String> reserved;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private int next = 1;

    /** One guest's order allowance. */
    private static final class Bucket {
        double tokens = BURST;
        long refilledAt;
    }

    /**
     * @param clock    used for the rate limit
     * @param reserved names guests may not take (the bots' names, "You"), case-insensitive
     */
    public GuestDesk(Clock clock, Set<String> reserved) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.reserved = reserved.stream().map(n -> n.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Signs someone in. A name that's already taken gets a number added
     * ("Richa" → "Richa 2"), so nobody is turned away for a clash.
     *
     * @throws IllegalArgumentException if the name is empty, too long, uses odd
     *         characters or is reserved, or the hall is full
     */
    public synchronized Guest join(String rawName) {
        String name = rawName == null ? "" : rawName.strip().replaceAll("\\s+", " ");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Pick a name of 2–20 letters or digits");
        }
        if (reserved.contains(name.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("\"" + name + "\" is taken by a correspondent; pick another");
        }
        if (byToken.size() >= MAX_GUESTS) {
            throw new IllegalArgumentException("The floor is full; watch on the big screen!");
        }
        Guest guest = new Guest(Guest.PREFIX + next++, unique(name), newToken());
        byToken.put(guest.token(), guest);
        byTrader.put(guest.traderId(), guest);
        Bucket bucket = new Bucket();
        bucket.refilledAt = clock.millis();
        buckets.put(guest.token(), bucket);
        return guest;
    }

    private String unique(String name) {
        String candidate = name;
        for (int n = 2; nameTaken(candidate); n++) {
            candidate = name + " " + n;
        }
        return candidate;
    }

    private boolean nameTaken(String name) {
        return byTrader.values().stream().anyMatch(g -> g.name().equalsIgnoreCase(name));
    }

    private String newToken() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** @return the guest holding this token, if any */
    public synchronized Optional<Guest> byToken(String token) {
        return Optional.ofNullable(token == null ? null : byToken.get(token));
    }

    /** @return the guest with this trader id, if any */
    public synchronized Optional<Guest> byTrader(String traderId) {
        return Optional.ofNullable(byTrader.get(traderId));
    }

    /** @return everyone who has joined, in joining order */
    public synchronized List<Guest> guests() {
        return List.copyOf(byTrader.values());
    }

    /**
     * Checks an order against the guest's limits and, if allowed, spends one token.
     *
     * @return {@code null} if the order may go ahead, otherwise why not
     */
    public synchronized String refusal(Guest guest, long qty) {
        if (qty <= 0) {
            return "Quantity must be at least 1";
        }
        if (qty > MAX_QTY) {
            return "Audience orders are limited to " + MAX_QTY + " shares";
        }
        Bucket bucket = buckets.get(guest.token());
        if (bucket == null) {
            return "Unknown guest; please join again";
        }
        long now = clock.millis();
        bucket.tokens = Math.min(BURST, bucket.tokens + (now - bucket.refilledAt) * ORDERS_PER_SECOND / 1000.0);
        bucket.refilledAt = now;
        if (bucket.tokens < 1) {
            return "Easy, trader! Max " + ORDERS_PER_SECOND + " orders a second";
        }
        bucket.tokens -= 1;
        return null;
    }
}
