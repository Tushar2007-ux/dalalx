package com.dalalx.server.api;

import com.dalalx.core.bots.BotLedger;
import com.dalalx.core.bots.BotRunner;
import com.dalalx.core.bots.BotStats;
import com.dalalx.core.bots.BotStatus;
import com.dalalx.core.bots.BotStyle;
import com.dalalx.core.bots.BotTuning;
import com.dalalx.core.bots.TradingBot;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST access to the bots ("Our Correspondents"): who they are and how they're
 * doing, plus pause/resume, the two dials, hiring and removing. An adapter
 * only; the bots live in core.
 */
@RestController
@RequestMapping("/api/bots")
public class BotController {

    /**
     * One bot as the UI sees it.
     *
     * @param id      trader id on its orders and trades
     * @param name    byline, e.g. "The Munim"
     * @param role    what kind of trader it is
     * @param line    one-sentence description
     * @param status  ACTIVE or PAUSED
     * @param running whether the bot thread is ticking at all
     * @param tuning  aggressiveness and size dials (0–4)
     * @param stats   orders, trades, position, cash and P&amp;L
     */
    public record BotInfo(String id, String name, String role, String line, BotStatus status, boolean running,
                          BotTuning tuning, BotStats stats) {
    }

    /** Body of POST /api/bots. */
    public record Hire(String name, BotStyle style) {
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

    @PutMapping("/{id}/tuning")
    public BotInfo tune(@PathVariable String id, @RequestBody BotTuning tuning) {
        if (!runner.tune(id, tuning)) {
            throw noSuchBot(id);
        }
        return info(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BotInfo hire(@RequestBody Hire hire) {
        if (hire.style() == null) {
            throw new IllegalArgumentException("style is required: one of " + List.of(BotStyle.values()));
        }
        TradingBot bot = hire.style().hire(hire.name());
        runner.hire(bot); // IllegalArgumentException (→ 400) if the name is taken
        return info(bot);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String id) {
        if (!runner.remove(id)) {
            throw noSuchBot(id);
        }
    }

    private BotInfo change(String id, BotStatus status) {
        if (!runner.setStatus(id, status)) {
            throw noSuchBot(id);
        }
        return info(find(id));
    }

    private TradingBot find(String id) {
        return runner.bot(id).orElseThrow(() -> noSuchBot(id));
    }

    private static ResponseStatusException noSuchBot(String id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "no bot called " + id);
    }

    private BotInfo info(TradingBot bot) {
        return new BotInfo(bot.id(), bot.name(), bot.role(), bot.line(),
                runner.status(bot.id()).orElse(BotStatus.PAUSED), runner.running(), bot.tuning(),
                ledger.stats(bot.id()));
    }
}
