package com.dalalx.server.api;

import com.dalalx.core.bots.BotLedger;
import com.dalalx.core.bots.BotRunner;
import com.dalalx.core.bots.BotStats;
import com.dalalx.core.bots.BotStatus;
import com.dalalx.core.bots.TradingBot;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST access to the bots: who they are, how they're doing, and a switch to
 * pause or resume each one. An adapter only; the bots live in core.
 */
@RestController
@RequestMapping("/api/bots")
public class BotController {

    /**
     * One bot as the UI sees it.
     *
     * @param id      trader id on its orders and trades
     * @param role    what kind of trader it is
     * @param status  ACTIVE or PAUSED
     * @param running whether the bot thread is ticking at all
     * @param stats   position, cash and P&amp;L
     */
    public record BotInfo(String id, String role, BotStatus status, boolean running, BotStats stats) {
    }

    private final BotRunner runner;
    private final BotLedger ledger;

    public BotController(BotRunner runner, BotLedger ledger) {
        this.runner = runner;
        this.ledger = ledger;
    }

    @GetMapping
    public List<BotInfo> bots() {
        return runner.bots().stream().map(this::info).toList();
    }

    @PostMapping("/{id}/pause")
    public BotInfo pause(@PathVariable String id) {
        return change(id, BotStatus.PAUSED);
    }

    @PostMapping("/{id}/resume")
    public BotInfo resume(@PathVariable String id) {
        return change(id, BotStatus.ACTIVE);
    }

    private BotInfo change(String id, BotStatus status) {
        if (!runner.setStatus(id, status)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no bot called " + id);
        }
        return info(runner.bot(id).orElseThrow());
    }

    private BotInfo info(TradingBot bot) {
        return new BotInfo(bot.id(), bot.role(), runner.status(bot.id()).orElseThrow(), runner.running(),
                ledger.stats(bot.id()));
    }
}
