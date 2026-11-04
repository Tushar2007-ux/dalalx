package com.dalalx.core.bots;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

/**
 * Drives all bots from <b>one</b> scheduler thread ("bots").
 *
 * <p>Every tick it asks each active bot whose turn it is to {@link TradingBot#act}
 * (zero, one or two times, depending on the bot's aggressiveness dial). Because
 * one thread runs every bot, a bot's own fields (its resting orders, the whale's
 * parent order) are thread-confined and need no locks; the same idea as one
 * worker per order book. Orders still go through the exchange's queues like
 * anyone else's.
 *
 * <p>Web threads may hire, remove, pause or tune bots while the bot thread is
 * ticking. Those requests only touch concurrent collections ({@link CopyOnWriteArrayList},
 * {@link ConcurrentHashMap}, {@link ConcurrentLinkedQueue}); the bot thread
 * picks the changes up at its next tick (e.g. it withdraws a paused or removed
 * bot's quotes).
 *
 * <p>{@link TickHook}s run on the same thread after the bots: that's how
 * scenarios script the market without any extra locking.
 *
 * <p>A bot that throws is logged and skipped for that tick; it can't stop
 * the others (the same isolation as the {@code EventBus}).
 */
public final class BotRunner {

    /** Something that wants to run on the bot thread once per tick (e.g. a scenario). */
    @FunctionalInterface
    public interface TickHook {
        void onTick(BotContext context);
    }

    private static final Logger LOG = System.getLogger(BotRunner.class.getName());

    private final List<TradingBot> bots = new CopyOnWriteArrayList<>();
    private final Map<String, BotStatus> status = new ConcurrentHashMap<>();
    private final Map<String, Boolean> withdrawn = new ConcurrentHashMap<>();
    private final Queue<TradingBot> retired = new ConcurrentLinkedQueue<>();
    private final List<TickHook> hooks = new CopyOnWriteArrayList<>();
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
        if (tickMillis <= 0) {
            throw new IllegalArgumentException("tickMillis must be > 0");
        }
        this.context = new BotContext(market, orders, random);
        this.tickMillis = tickMillis;
        bots.forEach(this::hire);
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
        bots.forEach(this::withdrawQuietly);
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
     * One round: retired bots withdraw their quotes, paused bots withdraw once,
     * each active bot whose turn it is acts, then the hooks run. Public so tests
     * can step the market by hand.
     */
    public void tick() {
        tick++;
        for (TradingBot gone; (gone = retired.poll()) != null; ) {
            withdrawQuietly(gone);
        }
        for (TradingBot bot : bots) {
            if (status.get(bot.id()) == BotStatus.PAUSED) {
                if (withdrawn.putIfAbsent(bot.id(), true) == null) {
                    withdrawQuietly(bot);
                }
            } else if (tick % bot.everyTicks() == 0) {
                int acts = bot.tuning().actsThisTurn(context.random());
                for (int i = 0; i < acts; i++) {
                    actQuietly(bot);
                }
            }
        }
        hooks.forEach(this::runHookQuietly);
    }

    private void actQuietly(TradingBot bot) {
        try {
            bot.act(context);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, () -> "bot " + bot.id() + " failed: " + e.getMessage());
        }
    }

    private void runHookQuietly(TickHook hook) {
        try {
            hook.onTick(context);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, () -> "tick hook failed: " + e.getMessage());
        }
    }

    private void withdrawQuietly(TradingBot bot) {
        try {
            bot.withdraw(context.orders());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, () -> "bot " + bot.id() + " could not withdraw: " + e.getMessage());
        }
    }

    /**
     * Adds a bot to the floor; it starts trading at the next tick.
     *
     * @throws IllegalArgumentException if a bot with the same id already exists
     */
    public synchronized void hire(TradingBot bot) {
        Objects.requireNonNull(bot, "bot must not be null");
        if (status.putIfAbsent(bot.id(), BotStatus.ACTIVE) != null) {
            throw new IllegalArgumentException("there is already a bot called " + bot.id());
        }
        bots.add(bot);
    }

    /** Takes a bot off the floor; its resting orders are cancelled at the next tick. @return false if unknown */
    public synchronized boolean remove(String botId) {
        Optional<TradingBot> bot = bot(botId);
        if (bot.isEmpty()) {
            return false;
        }
        bots.remove(bot.get());
        status.remove(botId);
        withdrawn.remove(botId);
        retired.add(bot.get());
        return true;
    }

    /** Pauses or resumes one bot. @return false if there is no such bot */
    public boolean setStatus(String botId, BotStatus newStatus) {
        Objects.requireNonNull(newStatus, "status must not be null");
        if (bot(botId).isEmpty()) {
            return false;
        }
        if (newStatus == BotStatus.ACTIVE) {
            withdrawn.remove(botId);
        }
        status.put(botId, newStatus);
        return true;
    }

    /** Turns a bot's dials. @return false if there is no such bot */
    public boolean tune(String botId, BotTuning tuning) {
        Optional<TradingBot> bot = bot(botId);
        bot.ifPresent(b -> b.tune(tuning));
        return bot.isPresent();
    }

    /** Registers code to run on the bot thread after every tick. */
    public void addHook(TickHook hook) {
        hooks.add(Objects.requireNonNull(hook, "hook must not be null"));
    }

    /** @return the bot's status, if it exists */
    public Optional<BotStatus> status(String botId) {
        return Optional.ofNullable(status.get(botId));
    }

    /** @return every bot, in the order they were hired */
    public List<TradingBot> bots() {
        return List.copyOf(bots);
    }

    /** @return the bot with that id, if any */
    public Optional<TradingBot> bot(String botId) {
        return bots.stream().filter(b -> b.id().equals(botId)).findFirst();
    }

    /** @return the display name for a trader id: a bot's byline, or the id itself */
    public String nameOf(String traderId) {
        return bot(traderId).map(TradingBot::name).orElse(traderId);
    }
}
