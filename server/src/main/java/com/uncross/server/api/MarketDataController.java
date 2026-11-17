package com.uncross.server.api;

import com.uncross.core.book.BookSnapshot;
import com.uncross.core.breaker.StatusBoard;
import com.uncross.core.exchange.Exchange;
import com.uncross.core.exchange.ListedStock;
import com.uncross.core.exchange.UnknownSymbolException;
import com.uncross.core.marketdata.Candle;
import com.uncross.core.marketdata.CandleAggregator;
import com.uncross.core.marketdata.CandleInterval;
import com.uncross.core.marketdata.TradeHistory;
import com.uncross.core.model.Trade;
import com.uncross.server.api.Dtos.SymbolInfo;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only REST endpoints for market data: the stock list, order books, recent
 * trades and chart candles.
 *
 * <p>The browser calls these once when a page loads, to draw the screen. After
 * that, live changes arrive over the WebSocket (see {@code MarketDataBridge}).
 */
@RestController
@RequestMapping("/api")
public class MarketDataController {

    private final StatusBoard statuses;

    static final int MAX_DEPTH = 50;
    static final int MAX_TRADES = 500;

    private final Exchange exchange;
    private final TradeHistory trades;
    private final CandleAggregator candles;

    public MarketDataController(Exchange exchange, TradeHistory trades, CandleAggregator candles,
                                StatusBoard statuses) {
        this.statuses = statuses;
        this.exchange = exchange;
        this.trades = trades;
        this.candles = candles;
    }

    /** {@code GET /api/symbols}: every listed stock with its last traded price. */
    @GetMapping("/symbols")
    public List<SymbolInfo> symbols() {
        return exchange.stocks().stream().map(this::info).toList();
    }

    /** What the listing form sends: a symbol, a company name and an opening price in paise. */
    public record NewListing(String symbol, String name, long referencePrice) {
    }

    /** Lists a new stock while the market runs: it gets its own worker thread at once. */
    @PostMapping("/symbols")
    @ResponseStatus(HttpStatus.CREATED)
    public SymbolInfo list(@RequestBody NewListing body) {
        String symbol = body.symbol() == null ? "" : body.symbol().strip().toUpperCase(java.util.Locale.ROOT);
        String name = body.name() == null || body.name().isBlank() ? symbol : body.name().strip();
        if (name.length() > 60) {
            throw new IllegalArgumentException("a company name is at most 60 characters");
        }
        if (body.referencePrice() < 100 || body.referencePrice() > 1_00_00_000_00L) {
            throw new IllegalArgumentException("an opening price is between ₹1 and ₹1,00,00,000");
        }
        return info(exchange.list(new ListedStock(symbol, name + " (simulated)", body.referencePrice())));
    }

    private SymbolInfo info(ListedStock stock) {
        long last = trades.lastPrice(stock.symbol()).orElse(stock.referencePrice());
        StatusBoard.Status status = statuses.status(stock.symbol());
        return new SymbolInfo(stock.symbol(), stock.name(), stock.referencePrice(), last,
                status.phase(), status.reason(), status.until(), status.since(),
                statuses.auction(stock.symbol()).orElse(null));
    }

    /** {@code GET /api/books/{symbol}?depth=10}: the top of the order book. */
    @GetMapping("/books/{symbol}")
    public BookSnapshot book(@PathVariable String symbol, @RequestParam(defaultValue = "10") int depth) {
        if (depth < 1 || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("depth must be between 1 and " + MAX_DEPTH);
        }
        return Futures.await(exchange.snapshot(symbol, depth));
    }

    /** {@code GET /api/trades/{symbol}?limit=50}: recent trades, newest first. */
    @GetMapping("/trades/{symbol}")
    public List<Trade> trades(@PathVariable String symbol, @RequestParam(defaultValue = "50") int limit) {
        requireListed(symbol);
        return trades.recent(symbol, Math.clamp(limit, 1, MAX_TRADES));
    }

    /** {@code GET /api/candles/{symbol}?interval=1m}: chart bars, oldest first. */
    @GetMapping("/candles/{symbol}")
    public List<Candle> candles(@PathVariable String symbol, @RequestParam(defaultValue = "1m") String interval) {
        requireListed(symbol);
        return candles.candles(symbol, CandleInterval.fromCode(interval));
    }

    private void requireListed(String symbol) {
        if (exchange.stock(symbol).isEmpty()) {
            throw new UnknownSymbolException(symbol);
        }
    }
}
