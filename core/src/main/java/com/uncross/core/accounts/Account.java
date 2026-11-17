package com.uncross.core.accounts;

import com.uncross.core.model.Money;
import com.uncross.core.model.Side;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One person's account: their cash, their shares, and what is set aside for
 * orders still waiting.
 *
 * <p><b>Reserving.</b> When you place an order the money (or shares) it could
 * use is set aside at once, so two orders can never both spend the same rupee.
 * "Available" is what you hold minus what is set aside. A fill turns the
 * reservation into a real payment; a cancel gives it back.
 *
 * <p>A BUY sets aside its limit price × size (for a market order, the last
 * price plus a 10% collar, since it has no price of its own). A SELL sets
 * aside the shares, which is what rules out short selling: you cannot set
 * aside shares you do not have.
 *
 * <p><b>Thread safety:</b> orders for different stocks are handled by
 * different worker threads at the same moment, and both may touch the same
 * account. Every method that reads or changes the figures is
 * {@code synchronized}, so each check-and-reserve happens as one step.
 *
 * <p><b>Principle:</b> encapsulation. Nothing outside can set the cash; it
 * only changes through the operations below, each of which keeps the books
 * balanced.
 */
public final class Account {

    /** What one waiting order has set aside. */
    private static final class Hold {
        final Side side;
        final long unit;  // paise set aside per share (BUY); 0 for SELL
        long remaining;   // shares still covered

        Hold(Side side, long unit, long remaining) {
            this.side = side;
            this.unit = unit;
            this.remaining = remaining;
        }
    }

    /** A copy of one holding, for display. */
    public record Holding(String symbol, long shares, long setAside) {
    }

    /** A copy of the whole account, safe to hand to another thread. */
    public record Statement(String name, String traderId, long cash, long cashSetAside, List<Holding> holdings) {
        public long availableCash() {
            return cash - cashSetAside;
        }
    }

    private final String name;
    private final String traderId;
    private final String pinHash;
    private long cash;
    private long cashSetAside;
    private final Map<String, Long> shares = new TreeMap<>();
    private final Map<String, Long> sharesSetAside = new HashMap<>();
    private final Map<Long, Hold> holds = new HashMap<>();
    /** Reservations made by the gate, waiting for the engine to say "accepted" or "rejected". */
    private final Map<String, Deque<Hold>> pending = new HashMap<>();

    Account(String name, String traderId, String pinHash, long startingCash) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.traderId = Objects.requireNonNull(traderId, "traderId must not be null");
        this.pinHash = Objects.requireNonNull(pinHash, "pinHash must not be null");
        this.cash = startingCash;
    }

    /**
     * Checks a BUY can be paid for and, if so, sets the money aside.
     *
     * @return why not, or empty if the money is now set aside
     */
    synchronized Optional<String> reserveBuy(String symbol, long unit, long qty) {
        long need = Math.multiplyExact(unit, qty);
        if (need > cash - cashSetAside) {
            return Optional.of("not enough cash: this order needs up to " + Money.format(need)
                    + " and you have " + Money.format(cash - cashSetAside) + " free");
        }
        cashSetAside += need;
        pendingFor(symbol).addLast(new Hold(Side.BUY, unit, qty));
        return Optional.empty();
    }

    /**
     * Checks a SELL is covered by shares you own and, if so, sets them aside.
     *
     * @return why not, or empty if the shares are now set aside
     */
    synchronized Optional<String> reserveSell(String symbol, long qty) {
        long free = shares.getOrDefault(symbol, 0L) - sharesSetAside.getOrDefault(symbol, 0L);
        if (qty > free) {
            return Optional.of("no short selling: you have " + free + " " + symbol + " free to sell, not " + qty);
        }
        sharesSetAside.merge(symbol, qty, Long::sum);
        pendingFor(symbol).addLast(new Hold(Side.SELL, 0, qty));
        return Optional.empty();
    }

    /** The engine accepted the order the last reservation on this stock was for. */
    synchronized void accepted(String symbol, long orderId) {
        Hold hold = pendingFor(symbol).pollFirst();
        if (hold != null) {
            holds.put(orderId, hold);
        }
    }

    /** The engine refused it after all (say, the stock is halted): give the reservation back. */
    synchronized void rejected(String symbol) {
        Hold hold = pendingFor(symbol).pollFirst();
        if (hold != null) {
            giveBack(symbol, hold, hold.remaining);
        }
    }

    synchronized void bought(long orderId, String symbol, long price, long qty) {
        cash -= Math.multiplyExact(price, qty);
        shares.merge(symbol, qty, Long::sum);
        Hold hold = holds.get(orderId);
        if (hold != null) {
            cashSetAside -= hold.unit * qty; // set aside at the limit; paid at the (equal or better) trade price
            settle(orderId, hold, qty);
        }
    }

    synchronized void sold(long orderId, String symbol, long price, long qty) {
        cash += Math.multiplyExact(price, qty);
        shares.merge(symbol, -qty, (a, b) -> a + b == 0 ? null : a + b);
        Hold hold = holds.get(orderId);
        if (hold != null) {
            sharesSetAside.merge(symbol, -qty, (a, b) -> a + b == 0 ? null : a + b);
            settle(orderId, hold, qty);
        }
    }

    /** The order was cancelled: whatever it still had set aside comes back. */
    synchronized void released(String symbol, long orderId) {
        Hold hold = holds.remove(orderId);
        if (hold != null) {
            giveBack(symbol, hold, hold.remaining);
        }
    }

    /** The order was made smaller: the difference comes back. */
    synchronized void reduced(String symbol, long orderId, long newRemaining) {
        Hold hold = holds.get(orderId);
        if (hold != null && newRemaining < hold.remaining) {
            giveBack(symbol, hold, hold.remaining - newRemaining);
            hold.remaining = newRemaining;
        }
    }

    private void settle(long orderId, Hold hold, long qty) {
        hold.remaining -= qty;
        if (hold.remaining <= 0) {
            holds.remove(orderId);
        }
    }

    private void giveBack(String symbol, Hold hold, long qty) {
        if (hold.side == Side.BUY) {
            cashSetAside -= hold.unit * qty;
        } else {
            sharesSetAside.merge(symbol, -qty, (a, b) -> a + b == 0 ? null : a + b);
        }
    }

    private Deque<Hold> pendingFor(String symbol) {
        return pending.computeIfAbsent(symbol, s -> new ArrayDeque<>());
    }

    boolean pinMatches(String pin) {
        return Pins.matches(pin, pinHash);
    }

    public String name() {
        return name;
    }

    public String traderId() {
        return traderId;
    }

    String pinHash() {
        return pinHash;
    }

    public synchronized Statement statement() {
        List<Holding> list = new ArrayList<>();
        shares.forEach((symbol, qty) -> list.add(new Holding(symbol, qty, sharesSetAside.getOrDefault(symbol, 0L))));
        return new Statement(name, traderId, cash, cashSetAside, List.copyOf(list));
    }
}
