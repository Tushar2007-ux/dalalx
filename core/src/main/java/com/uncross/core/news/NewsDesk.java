package com.uncross.core.news;

import com.uncross.core.bots.BotContext;
import com.uncross.core.bots.BotRunner;
import com.uncross.core.bots.FairValues;
import com.uncross.core.bots.MomentumBot;
import com.uncross.core.bots.PanicSellerBot;
import com.uncross.core.bots.Ticks;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * Company news that moves prices. Every minute or two a (made-up) story
 * breaks about one stock: good or bad, with an impact of 1.5–4.5%.
 *
 * <p>A story does two things, the way news does on a real floor:
 * <ol>
 *   <li>It changes what the stock is <em>worth</em>: the bots' fair value moves
 *       by the impact ({@link FairValues}), so The Munim re-centres its quotes
 *       and the new price sticks.</li>
 *   <li>It sets off a <em>reaction</em>: for a few turns, Momentum buys good
 *       news and Panic dumps bad news, so you see the jump on the tape.</li>
 * </ol>
 *
 * <p>The desk stays quiet while a storyline is running (it would spoil the
 * plot), and it can be switched off for a calm demo.
 *
 * <p><b>Threads:</b> it runs on the bots' thread as a {@link BotRunner.TickHook};
 * the web only flips {@code volatile} switches, which the next tick reads.
 */
public final class NewsDesk implements BotRunner.TickHook {

    /** One story, as published. */
    public record Flash(Instant at, String symbol, String text, String deck, long impactBp) {
        public boolean good() {
            return impactBp > 0;
        }
    }

    private record Template(String text, String deck) {
    }

    static final List<Template> GOOD = List.of(
            new Template("%s wins a large export order", "Analysts say the order book is now full for two years."),
            new Template("%s profit beats estimates", "Quarterly results come in well ahead of forecasts."),
            new Template("Brokers upgrade %s to Buy", "Three brokerages raise their price targets before the open."),
            new Template("%s announces a share buyback", "The company will buy back its own shares at a premium."));
    static final List<Template> BAD = List.of(
            new Template("%s profit misses estimates", "Quarterly results fall short of what the market expected."),
            new Template("Brokers cut %s to Sell", "Analysts warn that growth is slowing."),
            new Template("%s loses a major contract", "A large customer takes its business elsewhere."),
            new Template("%s delays a new launch", "The product slips by two quarters, the company says."));

    static final long MIN_IMPACT_BP = 150;
    static final long MAX_IMPACT_BP = 450;
    static final int REACTION_TURNS = 4;
    static final long REACTION_QTY = 400;

    private final FairValues fairValues;
    private final Clock clock;
    private final RandomGenerator random;
    private final BooleanSupplier quiet;
    private final Duration minGap;
    private final Duration maxGap;
    private final List<Consumer<Flash>> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean enabled = true;
    private volatile boolean breakNow;
    private Instant next;
    private Flash reacting;
    private int reactionLeft;

    public NewsDesk(FairValues fairValues, Clock clock, RandomGenerator random, BooleanSupplier quiet,
                    Duration minGap, Duration maxGap) {
        this.fairValues = Objects.requireNonNull(fairValues, "fairValues must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.quiet = Objects.requireNonNull(quiet, "quiet must not be null");
        if (minGap.isNegative() || maxGap.compareTo(minGap) < 0) {
            throw new IllegalArgumentException("need 0 <= minGap <= maxGap");
        }
        this.minGap = minGap;
        this.maxGap = maxGap;
    }

    @Override
    public void onTick(BotContext context) {
        if (reactionLeft > 0) {
            react(context);
            return;
        }
        Instant now = clock.instant();
        if (next == null) {
            next = now.plus(gap());
        }
        boolean due = enabled && !quiet.getAsBoolean() && !now.isBefore(next);
        if (breakNow || due) {
            breakNow = false;
            publish(context, now);
            next = now.plus(gap());
        }
    }

    private void publish(BotContext context, Instant now) {
        List<String> symbols = context.market().symbols();
        if (symbols.isEmpty()) {
            return;
        }
        String symbol = context.pick(symbols);
        boolean good = random.nextBoolean();
        Template template = context.pick(good ? GOOD : BAD);
        long impact = MIN_IMPACT_BP + random.nextLong(MAX_IMPACT_BP - MIN_IMPACT_BP + 1);
        Flash flash = new Flash(now, symbol, template.text().formatted(symbol), template.deck(), good ? impact : -impact);
        fairValues.shiftReference(symbol, flash.impactBp());
        reacting = flash;
        reactionLeft = REACTION_TURNS;
        listeners.forEach(listener -> listener.accept(flash));
    }

    /** One turn of the floor's reaction: Momentum chases good news, Panic runs from bad. */
    private void react(BotContext context) {
        reactionLeft--;
        boolean good = reacting.good();
        long last = context.market().lastPrice(reacting.symbol());
        long reach = reacting.impactBp() / 2; // reach halfway to the new value each turn
        context.orders().submit(new OrderRequest(reacting.symbol(), good ? MomentumBot.ID : PanicSellerBot.ID,
                good ? Side.BUY : Side.SELL, OrderType.IOC, Ticks.moveBy(last, reach), REACTION_QTY));
    }

    private Duration gap() {
        long span = maxGap.toMillis() - minGap.toMillis();
        return minGap.plusMillis(span == 0 ? 0 : random.nextLong(span + 1));
    }

    /** Breaks a story on the next tick, whatever the timer says (and even if news is switched off). */
    public void breakNews() {
        breakNow = true;
    }

    public void setEnabled(boolean on) {
        this.enabled = on;
    }

    public boolean enabled() {
        return enabled;
    }

    public void onFlash(Consumer<Flash> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }
}
