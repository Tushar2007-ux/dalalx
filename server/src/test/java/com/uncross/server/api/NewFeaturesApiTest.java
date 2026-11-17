package com.uncross.server.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The endpoints added with the second batch of features: accounts, amend,
 * iceberg and stop orders, listing, the rule bot, the tournament, news,
 * the day's figures and the journal. Real exchange, no mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class NewFeaturesApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String openAccount(String name) throws Exception {
        String body = send(post("/api/accounts"), "{\"name\":\"" + name + "\",\"pin\":\"2468\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    @Test
    void anAccountTradesWithItsOwnMoneyAndCannotShortSell() throws Exception {
        String token = openAccount("Asha");

        mvc.perform(get("/api/accounts/me").header(OrderController.SESSION_HEADER, token))
                .andExpect(jsonPath("$.traderId").value("A-ASHA"))
                .andExpect(jsonPath("$.availableCash").value(100_000_000));

        send(post("/api/orders").header(OrderController.SESSION_HEADER, token),
                "{\"symbol\":\"TCS\",\"side\":\"SELL\",\"type\":\"LIMIT\",\"price\":400000,\"qty\":5}")
                .andExpect(jsonPath("$.order.traderId").value("A-ASHA"))
                .andExpect(jsonPath("$.order.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value(containsString("no short selling")));

        send(post("/api/orders").header(OrderController.SESSION_HEADER, token),
                "{\"symbol\":\"TCS\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":100000,\"qty\":100}")
                .andExpect(jsonPath("$.order.status").value("NEW"));
        mvc.perform(get("/api/accounts/me").header(OrderController.SESSION_HEADER, token))
                .andExpect(jsonPath("$.cashSetAside").value(10_000_000));
    }

    @Test
    void signingInNeedsTheRightPinAndAStaleTokenIsUnauthorised() throws Exception {
        openAccount("Bina");
        send(post("/api/accounts/session"), "{\"name\":\"Bina\",\"pin\":\"0000\"}").andExpect(status().isBadRequest());
        String token = json.readTree(send(post("/api/accounts/session"), "{\"name\":\"bina\",\"pin\":\"2468\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(delete("/api/accounts/session").header(OrderController.SESSION_HEADER, token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/accounts/me").header(OrderController.SESSION_HEADER, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anOrderCanBeAmendedAndAnIcebergShowsOneSlice() throws Exception {
        String placed = send(post("/api/orders"),
                "{\"symbol\":\"HDFCBANK\",\"side\":\"BUY\",\"type\":\"ICEBERG\",\"price\":160000,\"qty\":1000,\"displayQty\":100}")
                .andExpect(jsonPath("$.order.status").value("NEW")).andReturn().getResponse().getContentAsString();
        long id = json.readTree(placed).at("/order/id").asLong();
        mvc.perform(get("/api/books/HDFCBANK")).andExpect(jsonPath("$.bids[0].totalQty").value(100));

        send(patch("/api/orders/" + id), "{\"price\":160000,\"qty\":500}")
                .andExpect(jsonPath("$.amended").value(true))
                .andExpect(jsonPath("$.keptPriority").value(true))
                .andExpect(jsonPath("$.order.remainingQty").value(500));
        send(patch("/api/orders/" + id), "{\"price\":159000,\"qty\":500}")
                .andExpect(jsonPath("$.keptPriority").value(false))
                .andExpect(jsonPath("$.order.id").value(greaterThan((int) id)));
    }

    @Test
    void aStopLossIsAcceptedAndWaits() throws Exception {
        send(post("/api/orders"), "{\"symbol\":\"TATAMOTORS\",\"side\":\"SELL\",\"type\":\"STOP_LOSS\",\"price\":90000,\"qty\":10}")
                .andExpect(jsonPath("$.order.type").value("STOP_LOSS"))
                .andExpect(jsonPath("$.order.status").value("NEW"));
        mvc.perform(get("/api/books/TATAMOTORS")).andExpect(jsonPath("$.asks").isEmpty());
    }

    @Test
    void aStockCanBeListedWhileTheMarketRuns() throws Exception {
        send(post("/api/symbols"), "{\"symbol\":\"wipro\",\"name\":\"Wipro\",\"referencePrice\":25000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("WIPRO"))
                .andExpect(jsonPath("$.name").value("Wipro (simulated)"));
        mvc.perform(get("/api/symbols")).andExpect(jsonPath("$[5].symbol").value("WIPRO"));
        mvc.perform(get("/api/wire?limit=1")).andExpect(jsonPath("$[0].text").value("WIPRO lists on Uncross"));
        send(post("/api/symbols"), "{\"symbol\":\"WIPRO\",\"referencePrice\":25000}").andExpect(status().isBadRequest());
    }

    @Test
    void aReaderCanBuildABotAndRunATournament() throws Exception {
        send(post("/api/bots/rules"), """
                {"name":"Dip Buyer","symbol":"INFY","when":"FALLS","amount":100,"side":"BUY","qty":50,"maxShares":500}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("DIPBUYER"))
                .andExpect(jsonPath("$.line").value(containsString("whenever the price falls 1.00%")));
        send(post("/api/bots/rules"), """
                {"name":"Nowhere","symbol":"NOPE","when":"EVERY","amount":5,"side":"BUY","qty":5,"maxShares":5}""")
                .andExpect(status().isBadRequest());

        send(post("/api/bots/tournament"), "{\"seconds\":60}")
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.scores.length()").value(greaterThan(5)));
        mvc.perform(delete("/api/bots/tournament")).andExpect(status().isNoContent());
        mvc.perform(get("/api/bots/tournament")).andExpect(jsonPath("$.state").value("IDLE"));
    }

    @Test
    void theNewsDeskCanBeSwitched() throws Exception {
        mvc.perform(get("/api/news/desk")).andExpect(jsonPath("$.enabled").value(false));
        send(put("/api/news/desk"), "{\"enabled\":true}").andExpect(jsonPath("$.enabled").value(true));
        send(put("/api/news/desk"), "{\"enabled\":false}").andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/news/break")).andExpect(status().isAccepted());
    }

    @Test
    void theDaysFiguresCoverEveryStock() throws Exception {
        send(post("/api/orders"), "{\"symbol\":\"RELIANCE\",\"side\":\"SELL\",\"type\":\"LIMIT\",\"price\":246000,\"qty\":7}");
        send(post("/api/orders"), "{\"symbol\":\"RELIANCE\",\"side\":\"BUY\",\"type\":\"MARKET\",\"price\":0,\"qty\":7}");

        String body = mvc.perform(get("/api/day")).andReturn().getResponse().getContentAsString();
        JsonNode day = json.readTree(body);
        JsonNode reliance = day.get("stocks").get(0);
        org.assertj.core.api.Assertions.assertThat(reliance.get("symbol").asText()).isEqualTo("RELIANCE");
        org.assertj.core.api.Assertions.assertThat(reliance.get("volume").asLong()).isGreaterThanOrEqualTo(7);
        org.assertj.core.api.Assertions.assertThat(day.get("biggest").get(0).get("buyer").asText()).isNotBlank();
    }

    @Test
    void withTheJournalOffThereIsNothingToRewind() throws Exception {
        mvc.perform(get("/api/journal")).andExpect(jsonPath("$.status.enabled").value(false));
        mvc.perform(get("/api/rewind")).andExpect(status().isNotFound());
        send(post("/api/rewind"), "{\"session\":\"../../etc/passwd\"}").andExpect(status().isBadRequest());
    }
}
