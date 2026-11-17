package com.uncross.server.api;

import com.uncross.core.accounts.Account;
import com.uncross.core.accounts.Accounts;
import com.uncross.core.bots.BotRunner;
import com.uncross.core.exchange.Exchange;
import com.uncross.core.exchange.ListedStock;
import com.uncross.core.marketdata.DayTape;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The day's figures for the end-of-day edition. */
@RestController
@RequestMapping("/api/day")
public class DayController {

    public record StockLine(String symbol, String name, long reference, long open, long high, long low, long last,
                            long volume, long turnover, long trades) {
    }

    public record BigTrade(java.time.Instant at, String symbol, long price, long qty, String buyer, String seller) {
    }

    public record Day(List<StockLine> stocks, List<BigTrade> biggest, long trades, long turnover) {
    }

    private final DayTape tape;
    private final Exchange exchange;
    private final BotRunner runner;
    private final Accounts accounts;

    public DayController(DayTape tape, Exchange exchange, BotRunner runner, Accounts accounts) {
        this.tape = tape;
        this.exchange = exchange;
        this.runner = runner;
        this.accounts = accounts;
    }

    @GetMapping
    public Day day() {
        DayTape.Summary summary = tape.summary();
        List<StockLine> stocks = exchange.stocks().stream().map(stock -> line(stock, summary)).toList();
        List<BigTrade> biggest = summary.biggest().stream().map(t -> new BigTrade(t.at(), t.symbol(), t.price(), t.qty(),
                name(t.buyerId()), name(t.sellerId()))).toList();
        return new Day(stocks, biggest, summary.trades(), summary.turnover());
    }

    private StockLine line(ListedStock stock, DayTape.Summary summary) {
        return summary.stocks().stream().filter(d -> d.symbol().equals(stock.symbol())).findFirst()
                .map(d -> new StockLine(d.symbol(), stock.name(), stock.referencePrice(), d.open(), d.high(), d.low(),
                        d.last(), d.volume(), d.turnover(), d.trades()))
                .orElse(new StockLine(stock.symbol(), stock.name(), stock.referencePrice(), 0, 0, 0,
                        stock.referencePrice(), 0, 0, 0));
    }

    private String name(String traderId) {
        if (OrderController.HUMAN.equals(traderId)) {
            return "You";
        }
        return accounts.byTrader(traderId).map(Account::name).orElseGet(() -> runner.nameOf(traderId));
    }
}
