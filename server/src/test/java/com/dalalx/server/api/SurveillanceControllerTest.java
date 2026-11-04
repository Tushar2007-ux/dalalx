package com.dalalx.server.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** Spoof through the REST API, get caught, get suspended. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class SurveillanceControllerTest {

    @Autowired
    private MockMvc mvc;

    private long bid(long price) throws Exception {
        String body = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"symbol\":\"RELIANCE\",\"traderId\":\"SPOOFER\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":%d,\"qty\":9800}"
                                .formatted(price)))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.order.id")).longValue();
    }

    @Test
    void spoofingIsCaughtAndTheTraderSuspended() throws Exception {
        long a = bid(245000);
        long b = bid(244995);
        long c = bid(244990);
        bid(240000); // a fourth bid, left resting: suspension must cancel it too
        for (long id : new long[] {a, b, c}) {
            mvc.perform(delete("/api/orders/" + id)).andExpect(status().isOk());
        }

        String cases = mvc.perform(get("/api/surveillance/cases"))
                .andExpect(jsonPath("$[*].ruleId").value(org.hamcrest.Matchers.hasItems("layering")))
                .andExpect(jsonPath("$[0].traderId").value("SPOOFER"))
                .andReturn().getResponse().getContentAsString();
        String caseId = JsonPath.read(cases, "$[0].id");

        mvc.perform(post("/api/surveillance/cases/" + caseId + "/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/books/RELIANCE")).andExpect(jsonPath("$.bids").isEmpty()); // the kept bid was cancelled
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"symbol\":\"TCS\",\"traderId\":\"SPOOFER\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":409000,\"qty\":1}"))
                .andExpect(jsonPath("$.order.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("trader suspended by market surveillance"));
        mvc.perform(get("/api/wire?limit=1"))
                .andExpect(jsonPath("$[0].text").value("The Spoofer suspended from trading; all resting orders cancelled"));

        mvc.perform(post("/api/surveillance/traders/SPOOFER/reinstate")).andExpect(status().isOk());
        mvc.perform(post("/api/surveillance/traders/SPOOFER/reinstate")).andExpect(status().isNotFound());
    }

    @Test
    void rulesCanBeSwitchedOff() throws Exception {
        mvc.perform(get("/api/surveillance/rules")).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(put("/api/surveillance/rules/pump").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(jsonPath("$[3].enabled").value(false));
        mvc.perform(put("/api/surveillance/rules/nope").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isNotFound());
    }
}
