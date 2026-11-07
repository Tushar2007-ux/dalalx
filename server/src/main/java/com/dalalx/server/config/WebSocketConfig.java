package com.dalalx.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Turns on WebSocket messaging with STOMP.
 *
 * <p><b>Why WebSocket as well as REST?</b> REST is request/response: the
 * browser asks, the server answers. For a live market that would mean polling
 * the server many times a second. A WebSocket stays open, so the server can
 * <em>push</em> each trade and book change the moment it happens.
 *
 * <p><b>STOMP</b> is a small messaging protocol on top of the socket. It adds
 * named <em>topics</em>: the browser subscribes to, say,
 * {@code /topic/trades.RELIANCE} and receives only those messages. Spring's
 * built-in "simple broker" keeps track of who subscribed to what.
 *
 * <p>Topics: {@code /topic/book.{SYMBOL}}, {@code /topic/trades.{SYMBOL}},
 * {@code /topic/orders} (activity), and, in later steps, {@code /topic/status},
 * {@code /topic/alerts}, {@code /topic/xray}, {@code /topic/headlines},
 * {@code /topic/bots}, {@code /topic/scenarios}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // localhost for the presenter; private network addresses for audience phones on the same Wi-Fi.
        registry.addEndpoint("/ws").setAllowedOriginPatterns(WebConfig.ALLOWED_ORIGINS);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
