package com.dalalx.server.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class AudienceControllerTest {

    @Autowired
    private MockMvc mvc;

    private String join(String name) throws Exception {
        String body = mvc.perform(post("/api/audience/join").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private void restingSell(long qty) throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(
                "{\"symbol\":\"INFY\",\"traderId\":\"MUNIM\",\"side\":\"SELL\",\"type\":\"LIMIT\",\"price\":154200,\"qty\":" + qty + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void aGuestJoinsTradesAndAppearsOnTheLeaderboard() throws Exception {
        String token = join("Richa");
        restingSell(5);

        mvc.perform(post("/api/audience/orders").header("X-Guest-Token", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"INFY\",\"side\":\"BUY\",\"type\":\"MARKET\",\"qty\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.traderId").value(org.hamcrest.Matchers.startsWith("GUEST-")))
                .andExpect(jsonPath("$.trades.length()").value(1));

        mvc.perform(get("/api/audience/me").header("X-Guest-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Richa"))
                .andExpect(jsonPath("$.holdings[0].symbol").value("INFY"))
                .andExpect(jsonPath("$.holdings[0].qty").value(5))
                .andExpect(jsonPath("$.holdings[0].avgPrice").value(154200));

        mvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.standings[*].name", hasItem("Richa")))
                .andExpect(jsonPath("$.standings[*].name", hasItem("You")))
                .andExpect(jsonPath("$.standings[*].name", hasItem("The Munim")))
                .andExpect(jsonPath("$.guests").isNumber());
    }

    @Test
    void ordersNeedAValidToken() throws Exception {
        mvc.perform(post("/api/audience/orders").header("X-Guest-Token", "forged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"INFY\",\"side\":\"BUY\",\"type\":\"MARKET\",\"qty\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Please join first"));
        mvc.perform(get("/api/audience/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void badNamesAndOversizedOrdersAreRefused() throws Exception {
        mvc.perform(post("/api/audience/join").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"The Munim\"}"))
                .andExpect(status().isBadRequest());
        String token = join("Surbhi");

        mvc.perform(post("/api/audience/orders").header("X-Guest-Token", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"INFY\",\"side\":\"BUY\",\"type\":\"MARKET\",\"qty\":5000}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("limited")));
    }

    @Test
    void floodingIsThrottled() throws Exception {
        String token = join("Flooder");
        int refused = 0;
        for (int i = 0; i < 10; i++) {
            int code = mvc.perform(post("/api/audience/orders").header("X-Guest-Token", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"symbol\":\"TCS\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":100000,\"qty\":1}"))
                    .andReturn().getResponse().getStatus();
            refused += code == 429 ? 1 : 0;
        }
        org.assertj.core.api.Assertions.assertThat(refused).isPositive();
        mvc.perform(post("/api/audience/cancel-all").header("X-Guest-Token", token)).andExpect(status().isOk());
    }

    @Test
    void inviteListsNetworkAddresses() throws Exception {
        mvc.perform(get("/api/audience/invite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses").isArray())
                .andExpect(jsonPath("$.guests").isNumber());
    }

    @Test
    void portfolioShowsThePresentersHoldings() throws Exception {
        restingSell(3);
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(
                "{\"symbol\":\"INFY\",\"side\":\"BUY\",\"type\":\"MARKET\",\"qty\":3}")).andExpect(status().isOk());

        mvc.perform(get("/api/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings[?(@.symbol=='INFY')].qty", hasItem(3)))
                .andExpect(jsonPath("$.stats.traderId").value("YOU"));
    }

    @Test
    void engineRoomReportsEveryWorkerThread() throws Exception {
        Thread.sleep(600); // let the bridge take two samples
        mvc.perform(get("/api/engine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workers.length()").value(5))
                .andExpect(jsonPath("$.workers[0].thread").value("worker-RELIANCE"))
                .andExpect(jsonPath("$.workers[0].capacity").value(100000))
                .andExpect(jsonPath("$.workers[0].arrivalsPerSecond.CLOCK").isNumber());
    }

    @Test
    void rushHourRunsFortyThousandCommandsThroughTheWorkers() throws Exception {
        mvc.perform(post("/api/engine/rush"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.producers").value(8))
                .andExpect(jsonPath("$.commands").value(40000))
                .andExpect(jsonPath("$.millis").isNumber());
    }
}
