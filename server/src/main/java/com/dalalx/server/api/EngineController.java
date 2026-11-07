package com.dalalx.server.api;

import com.dalalx.core.exchange.Exchange;
import com.dalalx.core.exchange.RushHour;
import com.dalalx.server.stream.EngineRoomBridge;
import com.dalalx.server.stream.EngineRoomBridge.EngineSnapshot;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Engine Room's REST side: the latest measurement (so the page isn't empty
 * while it waits for the stream) and the "rush hour" stress button.
 */
@RestController
public class EngineController {

    /** Eight producer threads, 5,000 commands each: 40,000 commands in well under a second. */
    static final int RUSH_PRODUCERS = 8;
    static final int RUSH_EACH = 5_000;

    private final EngineRoomBridge bridge;
    private final Exchange exchange;
    private final AtomicBoolean rushing = new AtomicBoolean();

    public EngineController(EngineRoomBridge bridge, Exchange exchange) {
        this.bridge = bridge;
        this.exchange = exchange;
    }

    /**
     * {@code POST /api/engine/rush}: fires a rush hour and answers when every
     * worker has handled its share. Only one rush at a time (409 otherwise).
     */
    @PostMapping("/api/engine/rush")
    public RushHour.Result rush() {
        if (!rushing.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A rush hour is already under way");
        }
        try {
            return Futures.await(RushHour.start(exchange, RUSH_PRODUCERS, RUSH_EACH));
        } finally {
            rushing.set(false);
        }
    }

    @GetMapping("/api/engine")
    public EngineSnapshot engine() {
        return bridge.latest();
    }
}
