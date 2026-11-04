package com.dalalx.server.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class XRayControllerTest {

    @Autowired
    private MockMvc mvc;

    private void order(String json) throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
    }

    @Test
    void xRaysYourOrderWithTheBotsNamedInTheStory() throws Exception {
        mvc.perform(get("/api/xray/latest")).andExpect(status().isNotFound());
        order("{\"symbol\":\"TATAMOTORS\",\"traderId\":\"WHALE\",\"side\":\"SELL\",\"type\":\"LIMIT\",\"price\":94235,\"qty\":30}");
        order("{\"symbol\":\"TATAMOTORS\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":94300,\"qty\":50}");

        mvc.perform(get("/api/xray/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("TATAMOTORS"))
                .andExpect(jsonPath("$.bookBefore[0].orders[0].traderId").value("WHALE"))
                .andExpect(jsonPath("$.fills[0].counterparty").value("WHALE"))
                .andExpect(jsonPath("$.steps[2].body").value(
                        "The Whale's order at ₹942.35 is first in line: 30 shares trade at its price. 20 still to buy."))
                .andExpect(jsonPath("$.steps[3].kind").value("STOP"));
        mvc.perform(get("/api/xray?limit=5")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/books/TATAMOTORS")).andExpect(jsonPath("$.bids[0].mainTrader").value("YOU"));
    }
}
