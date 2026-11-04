package com.dalalx.server.api;

import com.dalalx.core.xray.XRay;
import com.dalalx.core.xray.XRayDesk;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** X-Rays of the human trader's recent orders, for the explainer page. An adapter only. */
@RestController
@RequestMapping("/api/xray")
public class XRayController {

    private final XRayDesk desk;

    public XRayController(XRayDesk desk) {
        this.desk = desk;
    }

    /** {@code GET /api/xray?limit=10}: your recent orders' X-Rays, newest first. */
    @GetMapping
    public List<XRay> recent(@RequestParam(defaultValue = "10") int limit) {
        if (limit < 1 || limit > XRayDesk.CAPACITY) {
            throw new IllegalArgumentException("limit must be 1–" + XRayDesk.CAPACITY);
        }
        return desk.recent(OrderController.HUMAN, limit);
    }

    /** {@code GET /api/xray/latest}: the X-Ray of your most recent order. */
    @GetMapping("/latest")
    public XRay latest() {
        return desk.latest(OrderController.HUMAN).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no orders to X-Ray yet"));
    }

    /** {@code GET /api/xray/{orderId}}: one order's X-Ray. */
    @GetMapping("/{orderId}")
    public XRay one(@PathVariable long orderId) {
        return desk.find(orderId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no X-Ray for order " + orderId));
    }
}
