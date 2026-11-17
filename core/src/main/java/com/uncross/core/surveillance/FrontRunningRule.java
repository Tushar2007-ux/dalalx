package com.uncross.core.surveillance;

import com.uncross.core.events.EngineEvent;
import com.uncross.core.events.TradeExecuted;
import com.uncross.core.model.Money;
import com.uncross.core.model.Side;
import com.uncross.core.model.Trade;
import com.uncross.core.surveillance.Windows.TraderSymbol;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Front-running: trading <em>ahead</em> of a big order you know is coming,
 * then selling into it.
 *
 * <p>The pattern, for a big buyer:
 * <ol>
 *   <li>Trader A buys in the {@link #LEAD} before a block starts.</li>
 *   <li>Trader B then buys at least {@link #BLOCK} shares aggressively within
 *       {@link #BURST}: the block, which pushes the price up.</li>
 *   <li>A sells, during or soon after the block, at a higher price than A paid.</li>
 * </ol>
 * Any one step is innocent. All three, by a trader who is not the block's
 * owner, and at a profit, is the signature of someone tipped off. (Mirror it
 * for a big seller.)
 *
 * <p>The rule cannot know <em>how</em> A knew, only that A was early, small
 * and right. That is how real surveillance works too: the pattern opens the
 * case; people work out the leak.
 */
public final class FrontRunningRule implements SurveillanceRule {

    static final long BLOCK = 2_000;
    static final Duration BURST = Duration.ofSeconds(10);
    static final Duration LEAD = Duration.ofSeconds(15);
    static final Duration AFTER = Duration.ofSeconds(60);
    static final long MIN_ENTRY = 100;
    private static final Duration KEEP = LEAD.plus(AFTER);

    private record Fill(Instant at, String trader, Side side, long price, long qty, boolean aggressive, long orderId) {
    }

    private record Block(String trader, Side side, Instant start) {
    }

    private final Map<String, Deque<Fill>> fills = new HashMap<>();
    private final Map<String, Map<String, Block>> blocks = new HashMap<>(); // symbol -> trader|side -> block
    private final Windows.Cooldown<TraderSymbol> cooldown = new Windows.Cooldown<>(Duration.ofSeconds(60));

    @Override
    public String id() {
        return "front-running";
    }

    @Override
    public String name() {
        return "Front-running";
    }

    @Override
    public String threshold() {
        return "Bought ≤ 15 s before a ≥ 2,000-share block, sold into it at a profit";
    }

    @Override
    public Severity severity() {
        return Severity.CRITICAL;
    }

    @Override
    public List<Finding> inspect(EngineEvent event, ActivityView activity) {
        if (!(event instanceof TradeExecuted e)) {
            return List.of();
        }
        Trade t = e.trade();
        Deque<Fill> recent = fills.computeIfAbsent(t.symbol(), s -> new ArrayDeque<>());
        recent.addLast(new Fill(t.timestamp(), t.buyerId(), Side.BUY, t.price(), t.qty(),
                t.aggressorSide() == Side.BUY, t.buyOrderId()));
        recent.addLast(new Fill(t.timestamp(), t.sellerId(), Side.SELL, t.price(), t.qty(),
                t.aggressorSide() == Side.SELL, t.sellOrderId()));
        forgetOld(t.symbol(), recent, t.timestamp());
        noticeBlock(t, recent);
        List<Finding> findings = new ArrayList<>();
        check(t.symbol(), t.buyerId(), Side.BUY, t.price(), t.timestamp()).ifPresent(findings::add);
        check(t.symbol(), t.sellerId(), Side.SELL, t.price(), t.timestamp()).ifPresent(findings::add);
        return findings;
    }

    private void forgetOld(String symbol, Deque<Fill> recent, Instant now) {
        while (!recent.isEmpty() && Duration.between(recent.peekFirst().at(), now).compareTo(KEEP) > 0) {
            recent.removeFirst();
        }
        Map<String, Block> open = blocks.get(symbol);
        if (open != null) {
            open.values().removeIf(b -> Duration.between(b.start(), now).compareTo(AFTER) > 0);
        }
    }

    /** Is the trader who just hit the book now working a block? If so, note when it started. */
    private void noticeBlock(Trade t, Deque<Fill> recent) {
        String trader = t.aggressorSide() == Side.BUY ? t.buyerId() : t.sellerId();
        Side side = t.aggressorSide();
        Map<String, Block> open = blocks.computeIfAbsent(t.symbol(), s -> new HashMap<>());
        String key = trader + "|" + side;
        if (open.containsKey(key)) {
            return;
        }
        Instant since = t.timestamp().minus(BURST);
        long volume = 0;
        Instant first = t.timestamp();
        for (Fill f : recent) {
            if (f.aggressive() && f.trader().equals(trader) && f.side() == side && !f.at().isBefore(since)) {
                volume += f.qty();
                first = f.at().isBefore(first) ? f.at() : first;
            }
        }
        if (volume >= BLOCK) {
            open.put(key, new Block(trader, side, first));
        }
    }

    /** Did {@code trader} just unwind, into someone else's block, a position taken just before it began? */
    private Optional<Finding> check(String symbol, String trader, Side exitSide, long exitPrice, Instant now) {
        for (Block block : blocks.getOrDefault(symbol, Map.of()).values()) {
            if (block.trader().equals(trader) || block.side() == exitSide) {
                continue; // the block's owner, or trading with the block rather than out of a position
            }
            long qty = 0;
            long cost = 0;
            Set<Long> flagged = new HashSet<>();
            Instant earliest = null;
            for (Fill f : fills.get(symbol)) {
                boolean before = f.at().isBefore(block.start()) && !f.at().isBefore(block.start().minus(LEAD));
                if (before && f.trader().equals(trader) && f.side() == block.side()) {
                    qty += f.qty();
                    cost += f.price() * f.qty();
                    flagged.add(f.orderId());
                    earliest = earliest == null || f.at().isBefore(earliest) ? f.at() : earliest;
                }
            }
            if (qty < MIN_ENTRY) {
                continue;
            }
            long entry = cost / qty;
            boolean profit = block.side() == Side.BUY ? exitPrice > entry : exitPrice < entry;
            TraderSymbol key = new TraderSymbol(trader, symbol);
            if (profit && cooldown.tryReport(key, now)) {
                return Optional.of(finding(key, block, qty, entry, exitPrice, earliest, flagged));
            }
        }
        return Optional.empty();
    }

    private static Finding finding(TraderSymbol key, Block block, long qty, long entry, long exit,
                                   Instant entered, Set<Long> flagged) {
        long perShare = Math.abs(exit - entry);
        long lead = Duration.between(entered, block.start()).toMillis();
        Map<String, String> figures = new LinkedHashMap<>();
        figures.put(block.side() == Side.BUY ? "Bought before the block" : "Sold before the block",
                Money.groupIndian(qty) + " at " + Money.format(entry));
        figures.put("Block by", block.trader());
        figures.put("Unwound into it at", Money.format(exit));
        figures.put("Gain per share", Money.format(perShare));
        figures.put("Head start", SpoofingRule.seconds(lead));
        return new Finding(key.traderId(), key.symbol(),
                "traded " + SpoofingRule.seconds(lead) + " ahead of " + block.trader() + "'s block, then unwound into it",
                figures, flagged, new String[] {"Early, small, and right.", "The block's owner never knew."});
    }
}
