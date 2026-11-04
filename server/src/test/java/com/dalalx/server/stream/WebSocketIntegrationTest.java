package com.dalalx.server.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Starts the real server on a random port, connects a real STOMP WebSocket
 * client, places two crossing orders over REST, and checks that the trade and
 * the new book are pushed to the subscriber.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;

    @Test
    void tradeAndBookArePushedToSubscribers() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() { })
                .get(5, TimeUnit.SECONDS);
        BlockingQueue<Object> trades = subscribe(session, "/topic/trades.TCS", List.class);
        BlockingQueue<Object> books = subscribe(session, "/topic/book.TCS", Map.class);
        Thread.sleep(200); // let the subscriptions register before trading

        placeOrder("SELL", 409500, 10);
        placeOrder("BUY", 409500, 4);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> batch = (List<Map<String, Object>>) trades.poll(5, TimeUnit.SECONDS);
        assertThat(batch).isNotNull().singleElement().satisfies(trade -> {
            assertThat(((Number) trade.get("price")).longValue()).isEqualTo(409500);
            assertThat(((Number) trade.get("qty")).longValue()).isEqualTo(4);
        });
        Object book = books.poll(5, TimeUnit.SECONDS);
        assertThat(book).isNotNull();
        session.disconnect();
    }

    private void placeOrder(String side, long price, long qty) {
        ResponseEntity<String> response = rest.postForEntity("/api/orders", Map.of(
                "symbol", "TCS", "side", side, "type", "LIMIT", "price", price, "qty", qty), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private static BlockingQueue<Object> subscribe(StompSession session, String topic, Class<?> type) {
        BlockingQueue<Object> received = new LinkedBlockingQueue<>();
        session.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(payload);
            }
        });
        return received;
    }
}
