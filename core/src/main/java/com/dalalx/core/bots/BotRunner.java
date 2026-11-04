package com.dalalx.core.bots;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

/**
 * Drives all bots from <b>one</b> scheduler thread ("bots").
 *
 * <p>Every tick it asks each active bot whose turn it is to {@link TradingBot#act}.
 * Because one thread runs every bot, a bot's own fields (its resting orders,
 * the whale's parent order) are thread-confined and need no locks; the same
 * idea as one worker per order book. Orders still go through the exchange's
 * queues like anyone else's.
 *
 * <p>Pausing is the only cross-thread request: a web thread flips the bot's
 * status in a {@link ConcurrentHashMap}, and the bot thread notices on its
 * next tick and withdraws the bot's resting orders.
 *
 * <p>A bot that throws is logged and skipped for that tick; it can't stop
 * the others (the same isolation as the {@code EventBus}).
 */
public final class BotRunner {

    private static final Logger LOG = System.getLogger(BotRunner.class.getName());

    private final Map<String, TradingBot> bots = new LinkedHashMap<>();
    private final Map<String, BotStatus> status = new ConcurrentHashMap<>();
    private final Map<String, Boolean> withdrawn = new ConcurrentHashMap<>();
    private final BotContext context;
    private final long tickMillis;
    private ScheduledExecutorService scheduler;
    private long tick;

    /**
     * @param bots       the bots to run (ids must be unique)
     * @param market     what bots see
     * @param orders     how bots trade
     * @param random     the bots' dice
     * @param tickMillis time between ticks
     */
    public BotRunner(List<TradingBot> bots, MarketView market, OrderGateway orders,
                     RandomGenerator random, long tickMillis) {
        for (TradingBot bot : bots) {
            if (this.bots.putIfAbsent(bot.id(), bot) != null) {
                throw new IllegalArgumentException("duplicate bot id " + bot.id());
            }
            status.put(bot.id(), BotStatus.ACTIVE);
        }
        this.context = new BotContext(market, orders, random);
        if (tickMillis <= 0) {
            throw new IllegalArgumentException("tickMillis must be > 0");
        }
        this.tickMillis = tickMillis;
    }

    /** Starts ticking on a new daemon thread named "bots". */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bots");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::tickSafely, tickMillis, tickMillis, TimeUnit.MILLISECONDS);
    }

    /** Stops ticking and withdraws every bot's resting orders. */
    public synchronized void stop() throws InterruptedException {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdown();
        scheduler.awaitTermination(5, TimeUnit.SECONDS);
        scheduler = null;
        bots.values().forEach(this::withdrawQuietly);
    }

    /** @return whether the scheduler is running */
    public synchronized boolean running() {
        return scheduler != null;
    }

    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "bot tick failed", e);
        }
    }

    /**
     * One round: each active bot whose turn it is acts once; paused bots have
     * their orders withdrawn once. Public so tests can step the bots by hand.
     */
    public void tick() {
        tick++;
        for (TradingBot bot : bots.values()) {
            if (status.get(bot.id()) == BotStatus.PAUSED) {
                if (withdrawn.putIfAbsent(bot.id(), true) == null) {
                    withdrawQuietly(bot);
                }
            } else if (tick % bot.everyTicks() == 0) {
                actQuietly(bot);
            }
        }
    }

    private void actQuietly(TradingBot bot) {
        try {
            bot.act(context);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, () -> "bot " + bot.id() + " failed: " + e.getMessage());
        }
    }

    private void withdrawQuietly(TradingBot bot) {
        try {
            bot.withdraw(context.orders());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, () -> "bot " + bot.id() + " could not withdraw: " + e.getMessage());
        }
    }

    /** Pauses or resumes one bot. @return false if there is no such bot */
    public boolean setStatus(String botId, BotStatus newStatus) {
        Objects.requireNonNull(newStatus, "status must not be null");
        if (!bots.containsKey(botId)) {
            return false;
        }
        if (newStatus == BotStatus.ACTIVE) {
            withdrawn.remove(botId);
        }
        status.put(botId, newStatus);
        return true;
    }

    /** @return the bot's status, if it exists */
    public Optional<BotStatus> status(String botId) {
        return Optional.ofNullable(status.get(botId));
    }

    /** @return every bot, in the order they were given */
    public List<TradingBot> bots() {
        return List.copyOf(bots.values());
    }

    /** @return the bot with that id, if any */
    public Optional<TradingBot> bot(String botId) {
        return Optional.ofNullable(bots.get(botId));
    }
}
