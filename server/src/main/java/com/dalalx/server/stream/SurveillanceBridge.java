package com.dalalx.server.stream;

import com.dalalx.core.surveillance.SurveillanceDesk;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Pushes every new or updated case to {@code /topic/surveillance} (cases are rare: no throttling). */
@Component
public class SurveillanceBridge {

    public SurveillanceBridge(SurveillanceDesk desk, SimpMessagingTemplate broker) {
        desk.addListener(c -> broker.convertAndSend("/topic/surveillance", c));
    }
}
