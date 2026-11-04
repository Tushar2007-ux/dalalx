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
class NewsControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void listsTheStorylinesWithNothingRunning() throws Exception {
        mvc.perform(get("/api/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stories.length()").value(4))
                .andExpect(jsonPath("$.stories[0].id").value("flash-crash"))
                .andExpect(jsonPath("$.stories[0].durationSeconds").value(90))
                .andExpect(jsonPath("$.running.id").doesNotExist());
    }

    @Test
    void startingAnUnknownStoryIs404() throws Exception {
        mvc.perform(post("/api/scenarios/nope/start")).andExpect(status().isNotFound());
        mvc.perform(post("/api/scenarios/bubble/start")).andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("INFY"));
    }

    @Test
    void aBigMoveMakesTheWire() throws Exception {
        // TCS reference is ₹4,090; a trade at ₹4,140 is +1.2%.
        String sell = "{\"symbol\":\"TCS\",\"traderId\":\"WHALE\",\"side\":\"SELL\",\"type\":\"LIMIT\",\"price\":414000,\"qty\":10}";
        String buy = "{\"symbol\":\"TCS\",\"traderId\":\"YOU\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":414000,\"qty\":10}";
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(sell));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(buy));

        mvc.perform(get("/api/wire?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("TCS climbs 1% to ₹4,140.00"))
                .andExpect(jsonPath("$[0].tone").value("UP"));
        mvc.perform(get("/api/wire?limit=0")).andExpect(status().isBadRequest());
    }
}
