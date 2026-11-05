package com.dalalx.server.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Tests the order endpoints against the real exchange (no mocks), so they
 * check the full path: JSON → controller → worker thread → JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class OrderControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    private ResultActions place(String body) throws Exception {
        return mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String order(String symbol, String side, String type, long price, long qty) {
        return """
                {"symbol":"%s","side":"%s","type":"%s","price":%d,"qty":%d}""".formatted(symbol, side, type, price, qty);
    }

    @Test
    void placingALimitOrderThatDoesNotCrossRestsInTheBook() throws Exception {
        place(order("TCS", "BUY", "LIMIT", 400000, 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("NEW"))
                .andExpect(jsonPath("$.order.traderId").value("YOU"))
                .andExpect(jsonPath("$.rested").value(true))
                .andExpect(jsonPath("$.trades").isEmpty());
    }

    @Test
    void crossingOrdersReturnTheTradeAtTheRestingPrice() throws Exception {
        place(order("INFY", "SELL", "LIMIT", 150000, 10)).andExpect(status().isOk());

        place(order("INFY", "BUY", "LIMIT", 150500, 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("FILLED"))
                .andExpect(jsonPath("$.trades[0].price").value(150000))
                .andExpect(jsonPath("$.trades[0].qty").value(10));
    }

    @Test
    void marketOrderWithNoOneToTradeWithIsRejectedWithAReason() throws Exception {
        place(order("TATAMOTORS", "BUY", "MARKET", 0, 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason", containsString("no resting orders")));
    }

    @Test
    void aFatFingerOrderIsABadRequest() throws Exception {
        place(order("TCS", "BUY", "LIMIT", 409000, 10_00_001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("qty must be at most 1000000 but was 1000001"));
    }

    @Test
    void invalidQuantityIsABadRequest() throws Exception {
        place(order("TCS", "BUY", "LIMIT", 400000, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("qty")));
    }

    @Test
    void limitOrderWithoutAPriceIsABadRequest() throws Exception {
        place(order("TCS", "BUY", "LIMIT", 0, 5)).andExpect(status().isBadRequest());
    }

    @Test
    void unknownStockIsNotFound() throws Exception {
        place(order("ADANI", "BUY", "LIMIT", 100, 5))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("ADANI")));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        place("{\"symbol\":\"TCS\",\"side\":\"SIDEWAYS\"}").andExpect(status().isBadRequest());
    }

    @Test
    void cancellingARestingOrderWorksOnceThenExplainsWhy() throws Exception {
        String body = place(order("HDFCBANK", "BUY", "LIMIT", 160000, 7)).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/order/id").asLong();

        mvc.perform(delete("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));
        mvc.perform(delete("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(false))
                .andExpect(jsonPath("$.reason", containsString("CANCELLED")));
    }

    @Test
    void cancellingAnUnknownIdSaysSo() throws Exception {
        mvc.perform(delete("/api/orders/99999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(false))
                .andExpect(jsonPath("$.reason").value("no such order"));
    }

}
