package com.uncross.core.accounts;

import com.uncross.core.events.EngineEvent;
import com.uncross.core.events.EngineListener;
import com.uncross.core.events.OrderAccepted;
import com.uncross.core.events.OrderCancelled;
import com.uncross.core.events.OrderReduced;
import com.uncross.core.events.OrderRejected;
import com.uncross.core.events.TradeExecuted;
import com.uncross.core.matching.OrderGate;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import com.uncross.core.bots.Ticks;
import com.uncross.core.model.Trade;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToLongFunction;

/**
 * The pre-trade risk check: an account may not buy with money it does not
 * have, or sell shares it does not own.
 *
 * <p>It plays two roles, and that is what makes it work:
 * <ul>
 *   <li>As an {@link OrderGate} it sees every order <em>before</em> it is
 *       matched, and either refuses it or sets the money/shares aside.</li>
 *   <li>As an {@link EngineListener} it hears what then happened (accepted,
 *       traded, cancelled) and settles or releases what it set aside.</li>
 * </ul>
 * Orders from bots and guests are not accounts and pass straight through.
 *
 * <p><b>Why the gate and the events line up:</b> for one stock, the gate's
 * check and the "accepted"/"rejected" event that follows both run on that
 * stock's worker thread, one straight after the other. So the reservation the
 * gate just made is always the one the next event for that account and stock
 * is about. Thread confinement does the bookkeeping for free.
 *
 * <p><b>Patterns:</b> Strategy (it is one more {@link OrderGate}) and
 * Observer (it listens to engine events).
 */
public final class RiskDesk implements OrderGate, EngineListener {

    /** A market order has no price; it sets aside the last price plus 10%. */
    public static final long MARKET_COLLAR_BP = 1_000;

    private final Accounts accounts;
    private final ToLongFunction<String> referencePrice;
    private final Map<String, Long> lastPrice = new ConcurrentHashMap<>();

    public RiskDesk(Accounts accounts, ToLongFunction<String> referencePrice) {
        this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
        this.referencePrice = Objects.requireNonNull(referencePrice, "referencePrice must not be null");
    }

    @Override
    public Optional<String> refusal(OrderRequest request) {
        Optional<Account> account = accounts.byTrader(request.traderId());
        if (account.isEmpty()) {
            return Optional.empty(); // a bot or a guest: not ours to check
        }
        return request.side() == Side.BUY
                ? account.get().reserveBuy(request.symbol(), unitPrice(request), request.qty())
                : account.get().reserveSell(request.symbol(), request.qty());
    }

    /** What a BUY sets aside per share: its limit, or for a market-style order the collar price. */
    long unitPrice(OrderRequest request) {
        if (request.type() == OrderType.MARKET) {
            return collar(lastPrice.computeIfAbsent(request.symbol(), referencePrice::applyAsLong));
        }
        if (request.type() == OrderType.STOP_LOSS) {
            return collar(request.price()); // a triggered buy stop pays the market, which may be past the trigger
        }
        return request.price();
    }

    private static long collar(long price) {
        return Ticks.moveBy(price, MARKET_COLLAR_BP);
    }

    @Override
    public void onEvent(EngineEvent event) {
        switch (event) {
            case OrderAccepted e -> mine(e.order().traderId()).ifPresent(a -> a.accepted(e.symbol(), e.order().id()));
            case OrderRejected e -> mine(e.order().traderId()).ifPresent(a -> a.rejected(e.symbol()));
            case OrderCancelled e -> mine(e.order().traderId()).ifPresent(a -> a.released(e.symbol(), e.order().id()));
            case OrderReduced e -> mine(e.order().traderId())
                    .ifPresent(a -> a.reduced(e.symbol(), e.order().id(), e.order().remainingQty()));
            case TradeExecuted e -> settle(e.trade());
            default -> { }
        }
    }

    private void settle(Trade t) {
        lastPrice.put(t.symbol(), t.price());
        mine(t.buyerId()).ifPresent(a -> a.bought(t.buyOrderId(), t.symbol(), t.price(), t.qty()));
        mine(t.sellerId()).ifPresent(a -> a.sold(t.sellOrderId(), t.symbol(), t.price(), t.qty()));
    }

    private Optional<Account> mine(String traderId) {
        return Accounts.isAccount(traderId) ? accounts.byTrader(traderId) : Optional.empty();
    }

    /** The last traded price this desk knows for {@code symbol} (the reference price before any trade). */
    public long lastPrice(String symbol) {
        return lastPrice.getOrDefault(symbol, referencePrice.applyAsLong(symbol));
    }
}
