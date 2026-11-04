package com.dalalx.server.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** A crash through the REST API halts the stock, refuses orders, and makes the wire. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class CircuitBreakerApiTest {

    @Autowired
    private MockMvc mvc;

    private void order(String trader, String side, String type, long price, long qty) throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(
                "{\"symbol\":\"INFY\",\"traderId\":\"%s\",\"side\":\"%s\",\"type\":\"%s\",\"price\":%d,\"qty\":%d}"
                        .formatted(trader, side, type, price, qty)));
    }

    @Test
    void aTenPercentCrashHaltsInfy() throws Exception {
        order("MUNIM", "BUY", "LIMIT", 154200, 10);
        order("MUNIM", "BUY", "LIMIT", 138000, 10);
        order("PANIC", "SELL", "MARKET", 0, 20);

        mvc.perform(get("/api/symbols"))
                .andExpect(jsonPath("$[2].symbol").value("INFY"))
                .andExpect(jsonPath("$[2].phase").value("HALTED"))
                .andExpect(jsonPath("$[2].reason").value(Matchers.startsWith("INFY fell 10.5% in 60 seconds")))
                .andExpect(jsonPath("$[2].until").exists());
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"symbol\":\"INFY\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":138000,\"qty\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value(Matchers.startsWith("trading halted: INFY fell")));
        mvc.perform(get("/api/wire?limit=1"))
                .andExpect(jsonPath("$[0].text").value("Circuit breaker halts trading in INFY"));
    }
}
