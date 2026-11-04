package com.dalalx.server.stream;

import com.dalalx.core.book.BookSnapshot;
import com.dalalx.core.events.BookChanged;
import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.EngineListener;
import com.dalalx.core.events.EventBus;
import com.dalalx.core.events.OrderAccepted;
import com.dalalx.core.events.OrderCancelled;
import com.dalalx.core.events.OrderRejected;
import com.dalalx.core.events.OrderView;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.model.Trade;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Forwards engine events to browsers over the WebSocket.
 *
 * <p>It's an {@link EngineListener} (Observer) registered on the core
 * {@link EventBus}, and the only class that knows about both the engine's
 * events and Spring's messaging. That makes it the "bridge" between core and web.
 *
 * <p><b>Throttling:</b> a busy market can change a book hundreds of times a
 * second. Sending every change would flood the browser and make the screen
 * stutter. So:
 * <ul>
 *   <li><b>Books are coalesced:</b> only the <em>latest</em> snapshot per symbol
 *       is kept, and it is sent at most every 100 ms (10 per second). A newer
 *       snapshot simply replaces an unsent older one, which nobody needs anymore.</li>
 *   <li><b>Trades are batched:</b> every trade matters (it's history), so none
 *       are dropped. They're collected and sent as one list every 100 ms.</li>
 * </ul>
 *
 * <p><b>Threading:</b> {@link #onEvent} runs on the exchange's worker threads,
 * so it must be very quick: it only puts the event into a concurrent map or
 * queue. The actual sending happens on Spring's scheduler thread in {@link #flush()}.
 */
@Component
public class MarketDataBridge implements EngineListener {

    /** A user-facing order event for the activity log ("the Wire"). */
    public record OrderActivity(String kind, String symbol, long orderId, String traderId, String side,
                                String type, long price, long qty, long remainingQty, String reason) {
    }

    private final SimpMessagingTemplate broker;
    private final Map<String, BookSnapshot> latestBooks = new ConcurrentHashMap<>();
    private final Map<String, Queue<Trade>> pendingTrades = new ConcurrentHashMap<>();
    private final Queue<OrderActivity> pendingActivity = new ConcurrentLinkedQueue<>();

    public MarketDataBridge(SimpMessagingTemplate broker, EventBus bus) {
        this.broker = broker;
        bus.register(this);
    }

    @Override
    public void onEvent(EngineEvent event) {
        switch (event) {
            case BookChanged e -> latestBooks.put(e.symbol(), e.book()); // newer replaces older
            case TradeExecuted e -> pendingTrades
                    .computeIfAbsent(e.symbol(), s -> new ConcurrentLinkedQueue<>()).add(e.trade());
            case OrderAccepted e -> pendingActivity.add(activity("ACCEPTED", e.order(), null));
            case OrderRejected e -> pendingActivity.add(activity("REJECTED", e.order(), e.reason()));
            case OrderCancelled e -> pendingActivity.add(activity("CANCELLED", e.order(), e.reason()));
        }
    }

    private static OrderActivity activity(String kind, OrderView o, String reason) {
        return new OrderActivity(kind, o.symbol(), o.id(), o.traderId(), o.side().name(), o.type().name(),
                o.limitPrice(), o.originalQty(), o.remainingQty(), reason);
    }

    /** Every 100 ms: send the latest book per symbol, and all trades and activity since last time. */
    @Scheduled(fixedRateString = "${dalalx.stream.flush-ms:100}")
    public void flush() {
        for (String symbol : List.copyOf(latestBooks.keySet())) {
            BookSnapshot book = latestBooks.remove(symbol);
            if (book != null) {
                broker.convertAndSend("/topic/book." + symbol, book);
            }
        }
        pendingTrades.forEach((symbol, queue) -> {
            List<Trade> batch = drain(queue);
            if (!batch.isEmpty()) {
                broker.convertAndSend("/topic/trades." + symbol, batch);
            }
        });
        List<OrderActivity> activity = drain(pendingActivity);
        if (!activity.isEmpty()) {
            broker.convertAndSend("/topic/orders", activity);
        }
    }

    private static <T> List<T> drain(Queue<T> queue) {
        List<T> batch = new ArrayList<>();
        for (T item = queue.poll(); item != null; item = queue.poll()) {
            batch.add(item);
        }
        return batch;
    }
}
