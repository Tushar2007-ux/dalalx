package com.dalalx.core.scenarios;

import com.dalalx.core.bots.BotContext;
import com.dalalx.core.bots.BotRunner;
import com.dalalx.core.bots.BotStatus;
import com.dalalx.core.bots.BotTuning;
import com.dalalx.core.bots.MarketView;
import com.dalalx.core.bots.Ticks;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.core.model.OrderType;
import com.dalalx.core.model.Side;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What a {@link Scenario} is allowed to do: read prices, send orders in a
 * named trader's name (the storyline "puts words in a bot's mouth"), and turn
 * bots' dials or pause them.
 *
 * <p>The stage remembers every bot's original dials and status the first time
 * a scenario touches them, and {@link #restore()} puts them all back, so a
 * scenario can never leave the market permanently changed (a simple Memento).
 *
 * <p>Used only on the bot thread, so it needs no locks.
 */
public final class ScenarioStage {

    private final BotContext context;
    private final BotRunner runner;
    private final Map<String, BotTuning> savedTunings = new LinkedHashMap<>();
    private final Map<String, BotStatus> savedStatus = new LinkedHashMap<>();

    public ScenarioStage(BotContext context, BotRunner runner) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
    }

    /** @return the market as bots see it */
    public MarketView market() {
        return context.market();
    }

    /** @return the last price of {@code symbol} */
    public long last(String symbol) {
        return context.market().lastPrice(symbol);
    }

    /**
     * Sends an immediate-or-cancel order in {@code traderId}'s name, priced
     * {@code basisPoints} away from the last price.
     */
    public MatchResult sweep(String traderId, String symbol, Side side, long basisPoints, long qty) {
        long price = Ticks.moveBy(last(symbol), basisPoints);
        return context.orders().submit(new OrderRequest(symbol, traderId, side, OrderType.IOC, price, qty));
    }

    /** Turns a bot's dials for the length of the story (no-op if the bot was removed). */
    public void tune(String botId, BotTuning tuning) {
        runner.bot(botId).ifPresent(bot -> {
            savedTunings.putIfAbsent(botId, bot.tuning());
            bot.tune(tuning);
        });
    }

    /** Pauses a bot for the length of the story. */
    public void pause(String botId) {
        runner.status(botId).ifPresent(status -> {
            savedStatus.putIfAbsent(botId, status);
            runner.setStatus(botId, BotStatus.PAUSED);
        });
    }

    /** Puts back every dial and status this stage changed. */
    public void restore() {
        savedTunings.forEach(runner::tune);
        savedStatus.forEach(runner::setStatus);
        savedTunings.clear();
        savedStatus.clear();
    }
}
