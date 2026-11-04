package com.dalalx.server.stream;

import com.dalalx.core.headlines.HeadlineWriter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes every new headline to {@code /topic/wire} the moment it is filed.
 * Headlines are rare (a few a minute), so unlike the book there is nothing to
 * throttle.
 */
@Component
public class WireBridge {

    public WireBridge(HeadlineWriter writer, SimpMessagingTemplate broker) {
        writer.subscribe(headline -> broker.convertAndSend("/topic/wire", headline));
    }
}
