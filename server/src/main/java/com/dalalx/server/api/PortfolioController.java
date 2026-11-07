package com.dalalx.server.api;

import com.dalalx.core.bots.BotLedger;
import com.dalalx.core.bots.BotStats;
import com.dalalx.core.bots.Holding;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The presenter's own portfolio: shares held, average prices and profit.
 * An adapter over the core {@link BotLedger}, which keeps score for everyone.
 */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    /** What the portfolio panel shows. */
    public record Portfolio(BotStats stats, List<Holding> holdings) {
    }

    private final BotLedger ledger;

    public PortfolioController(BotLedger ledger) {
        this.ledger = ledger;
    }

    /** {@code GET /api/portfolio} */
    @GetMapping
    public Portfolio portfolio() {
        return new Portfolio(ledger.stats(OrderController.HUMAN), ledger.holdings(OrderController.HUMAN));
    }
}
