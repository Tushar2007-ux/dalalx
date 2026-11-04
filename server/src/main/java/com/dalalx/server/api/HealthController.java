package com.dalalx.server.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint that reports whether the server is up.
 *
 * <p>It is the first, simplest adapter in the server: the frontend calls it
 * (through the Vite dev proxy) to confirm the whole stack is wired together.
 * The response body is an immutable {@link HealthResponse} record, which Spring
 * serialises to JSON automatically.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * Immutable value object for the health response.
     *
     * @param status always {@code "ok"} while the server is running
     */
    public record HealthResponse(String status) {
    }

    /**
     * Handles {@code GET /api/health}.
     *
     * @return {@code {"status":"ok"}}
     */
    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok");
    }
}
