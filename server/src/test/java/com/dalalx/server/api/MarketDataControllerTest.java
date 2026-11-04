package com.dalalx.server.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Tests the read-only market-data endpoints after one RELIANCE trade at ₹2,460.00. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MarketDataControllerTest {

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    void oneTradeAndOneRestingBid() throws Exception {
        post("SELL", 246000, 10);
        post("BUY", 246000, 10);   // trades at ₹2,460.00
        post("BUY", 245500, 4);    // rests
    }

    private void post(String side, long price, long qty) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"RELIANCE","side":"%s","type":"LIMIT","price":%d,"qty":%d}"""
                                .formatted(side, price, qty)))
                .andExpect(status().isOk());
    }

    @Test
    void symbolsListsAllFiveStocksWithTheirLastPrice() throws Exception {
        mvc.perform(get("/api/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].lastPrice").value(246000))
                .andExpect(jsonPath("$[1].lastPrice").value(409000)) // TCS: no trades yet, so its reference price
                .andExpect(jsonPath("$[1].phase").value("OPEN"));
    }

    @Test
    void bookShowsTheRestingBid() throws Exception {
        mvc.perform(get("/api/books/RELIANCE").param("depth", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.bids[0].price").value(245500))
                .andExpect(jsonPath("$.bids[0].totalQty").value(4));
    }

    @Test
    void tradesAreNewestFirst() throws Exception {
        mvc.perform(get("/api/trades/RELIANCE").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].price").value(246000))
                .andExpect(jsonPath("$[0].aggressorSide").value("BUY"));
    }

    @Test
    void candlesSummariseTheTrade() throws Exception {
        mvc.perform(get("/api/candles/RELIANCE").param("interval", "1m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].open").value(246000))
                .andExpect(jsonPath("$[0].volume").value(10));
    }

    @Test
    void badDepthOrIntervalIsABadRequest() throws Exception {
        mvc.perform(get("/api/books/RELIANCE").param("depth", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/candles/RELIANCE").param("interval", "2h")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownStockIsNotFoundEverywhere() throws Exception {
        mvc.perform(get("/api/books/ADANI")).andExpect(status().isNotFound());
        mvc.perform(get("/api/trades/ADANI")).andExpect(status().isNotFound());
        mvc.perform(get("/api/candles/ADANI")).andExpect(status().isNotFound());
    }
}
