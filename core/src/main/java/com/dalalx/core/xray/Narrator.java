package com.dalalx.core.xray;

import com.dalalx.core.book.LevelDetail;
import com.dalalx.core.model.Money;
import com.dalalx.core.model.OrderStatus;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import com.dalalx.core.xray.XRay.Fill;
import com.dalalx.core.xray.XRay.Step;
import com.dalalx.core.xray.XRay.StepKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Writes the X-Ray's plain-English explanation of a match, step by step:
 * the order arrives → the best opposite price is checked → each fill →
 * why matching stopped → what happened to the rest.
 *
 * <p>A pure function of the facts (no state, no clock), so every sentence is
 * unit-testable and the same order always gets the same story.
 */
public final class Narrator {

    private final Function<String, String> names;

    /** @param names trader id → display name ("MUNIM" → "The Munim") */
    public Narrator(Function<String, String> names) {
        this.names = names;
    }

    /** Facts about the order that the steps are built from. */
    record Facts(String symbol, Side side, OrderType type, long limit, long qty, List<LevelDetail> book,
                 List<Fill> fills, OrderStatus status, long remaining, String reason) {
    }

    List<Step> narrate(Facts f) {
        List<Step> steps = new ArrayList<>();
        steps.add(arrive(f));
        if (f.status() == OrderStatus.REJECTED && f.reason() != null && f.reason().startsWith("trading halted")) {
            steps.add(new Step(StepKind.OUTCOME, "Refused at the door", capitalise(f.reason()) + ". It never reached the book."));
            return steps;
        }
        steps.add(check(f));
        long left = f.qty();
        for (int i = 0; i < f.fills().size(); i++) {
            Fill fill = f.fills().get(i);
            left -= fill.qty();
            steps.add(fill(f, fill, i, left));
        }
        if (f.remaining() > 0) {
            stop(f).ifPresent(steps::add);
        }
        steps.add(outcome(f));
        return steps;
    }

    private Step arrive(Facts f) {
        String verb = f.side() == Side.BUY ? "buy" : "sell";
        String price = switch (f.type()) {
            case MARKET -> "at whatever price the book offers";
            case LIMIT -> "at " + (f.side() == Side.BUY ? "no more than " : "no less than ") + Money.format(f.limit()) + " a share";
            case IOC -> "at " + (f.side() == Side.BUY ? "no more than " : "no less than ") + Money.format(f.limit())
                    + ", immediately or not at all";
        };
        return new Step(StepKind.ARRIVE, "The order arrives",
                "A " + typeName(f.type()) + " order to " + verb + " " + Money.groupIndian(f.qty()) + " " + f.symbol() + " " + price
                        + ". It goes to the " + (f.side() == Side.BUY ? "sell" : "buy") + " side of the book first.");
    }

    private Step check(Facts f) {
        if (f.book().isEmpty()) {
            return new Step(StepKind.CHECK, "Check the best " + oppositeWord(f.side()),
                    "There are no " + oppositeName(f.side()) + "s in the book at all, so nothing can trade.");
        }
        long best = f.book().get(0).price();
        boolean crosses = crosses(f, best);
        return new Step(StepKind.CHECK, "Check the best " + oppositeWord(f.side()),
                "The best " + oppositeName(f.side()) + " is at " + Money.format(best) + ". "
                        + (crosses ? (f.type() == OrderType.MARKET ? "A market order takes it" : "That is within your limit")
                                + ", so the order can trade straight away."
                                : "That is beyond your limit of " + Money.format(f.limit()) + ", so nothing trades."));
    }

    private Step fill(Facts f, Fill fill, int n, long left) {
        String who = names.apply(fill.counterparty());
        String tail = left > 0 ? Money.groupIndian(left) + " still to " + (f.side() == Side.BUY ? "buy." : "sell.") : "The order is complete.";
        return new Step(StepKind.FILL, circled(n) + " " + Money.groupIndian(fill.qty()) + " shares at " + Money.format(fill.price()),
                who + "'s order at " + Money.format(fill.price()) + " is " + (n == 0 ? "first in line" : "next")
                        + ": " + Money.groupIndian(fill.qty()) + " shares trade at its price. " + tail);
    }

    private Optional<Step> stop(Facts f) {
        int lastLevel = f.fills().isEmpty() ? -1 : f.fills().get(f.fills().size() - 1).level();
        long touched = f.fills().isEmpty() ? 0 : f.fills().stream().filter(x -> x.level() == lastLevel).mapToLong(Fill::qty).sum();
        boolean lastLevelLeftOver = lastLevel >= 0 && touched < f.book().get(lastLevel).totalQty();
        int next = lastLevelLeftOver ? lastLevel : lastLevel + 1;
        if (f.fills().isEmpty() && !f.book().isEmpty() && !crosses(f, f.book().get(0).price())) {
            return Optional.empty(); // the CHECK step already said why
        }
        if (next < f.book().size() && !crosses(f, f.book().get(next).price())) {
            long price = f.book().get(next).price();
            return Optional.of(new Step(StepKind.STOP, "The next " + oppositeWord(f.side()) + " is too " + (f.side() == Side.BUY ? "dear" : "cheap"),
                    "The next price is " + Money.format(price) + ", " + (f.side() == Side.BUY ? "above" : "below")
                            + " your limit of " + Money.format(f.limit()) + ". Matching stops here."));
        }
        return Optional.of(new Step(StepKind.STOP, "The book runs out",
                "There are no more " + oppositeName(f.side()) + "s to trade with. Matching stops with "
                        + Money.groupIndian(f.remaining()) + " shares unfilled."));
    }

    private Step outcome(Facts f) {
        String rest = Money.groupIndian(f.remaining());
        return switch (f.status()) {
            case FILLED -> new Step(StepKind.OUTCOME, "Filled in full",
                    "All " + Money.groupIndian(f.qty()) + " shares traded" + averageText(f) + ". Nothing is left in the book.");
            case NEW, PARTIALLY_FILLED -> new Step(StepKind.OUTCOME, "The rest waits in the book",
                    "The remaining " + rest + " shares rest as a " + (f.side() == Side.BUY ? "bid" : "ask") + " at "
                            + Money.format(f.limit()) + ". They keep their place in the queue until someone trades with them or you cancel.");
            case CANCELLED -> new Step(StepKind.OUTCOME, "The rest is cancelled",
                    "A " + typeName(f.type()) + " order never waits in the book, so the remaining " + rest
                            + " shares are cancelled (" + f.reason() + ").");
            case REJECTED -> new Step(StepKind.OUTCOME, "Rejected",
                    "Nothing traded, so the order is rejected (" + f.reason() + ").");
        };
    }

    private static String averageText(Facts f) {
        if (f.fills().size() < 2) {
            return f.fills().isEmpty() ? "" : " at " + Money.format(f.fills().get(0).price());
        }
        long value = f.fills().stream().mapToLong(x -> x.price() * x.qty()).sum();
        long qty = f.fills().stream().mapToLong(Fill::qty).sum();
        return " at an average of " + Money.format(value / qty);
    }

    private static boolean crosses(Facts f, long price) {
        if (f.type() == OrderType.MARKET) {
            return true;
        }
        return f.side() == Side.BUY ? price <= f.limit() : price >= f.limit();
    }

    private static String typeName(OrderType type) {
        return switch (type) {
            case LIMIT -> "limit";
            case MARKET -> "market";
            case IOC -> "immediate-or-cancel";
        };
    }

    private static String oppositeName(Side side) {
        return side == Side.BUY ? "seller" : "buyer";
    }

    private static String oppositeWord(Side side) {
        return side == Side.BUY ? "ask" : "bid";
    }

    private static String circled(int n) {
        return n < 20 ? String.valueOf((char) ('①' + n)) : "(" + (n + 1) + ")";
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
