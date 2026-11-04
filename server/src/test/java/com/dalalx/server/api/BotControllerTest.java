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
class BotControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void listsTheFourBotsWithTheirScores() throws Exception {
        mvc.perform(get("/api/bots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value("MUNIM"))
                .andExpect(jsonPath("$[0].role").value("Market Maker"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].running").value(false)) // bots are off in tests
                .andExpect(jsonPath("$[0].stats.pnl").value(0));
    }

    @Test
    void scoresTradesMadeUnderABotsName() throws Exception {
        String sell = "{\"symbol\":\"TCS\",\"traderId\":\"WHALE\",\"side\":\"SELL\",\"type\":\"LIMIT\",\"price\":409000,\"qty\":10}";
        String buy = "{\"symbol\":\"TCS\",\"traderId\":\"YOU\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":409000,\"qty\":10}";
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(sell)).andExpect(status().isOk());
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(buy)).andExpect(status().isOk());

        mvc.perform(get("/api/bots"))
                .andExpect(jsonPath("$[?(@.id=='WHALE')].stats.positions.TCS").value(-10))
                .andExpect(jsonPath("$[?(@.id=='WHALE')].stats.cash").value(4_090_000));
    }

    @Test
    void pausesAndResumesABot() throws Exception {
        mvc.perform(post("/api/bots/PANIC/pause")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(post("/api/bots/PANIC/resume")).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void unknownBotIs404WithAnErrorMessage() throws Exception {
        mvc.perform(post("/api/bots/GHOST/pause"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("no bot called GHOST"));
    }
}
