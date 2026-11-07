package com.dalalx.server.stream;

import com.dalalx.server.api.AudienceController.Board;
import com.dalalx.server.audience.FloorRoster;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pushes the leaderboard to {@code /topic/leaderboard} once a second, for the big screen and every phone. */
@Component
public class AudienceBridge {

    private final FloorRoster roster;
    private final SimpMessagingTemplate broker;

    public AudienceBridge(FloorRoster roster, SimpMessagingTemplate broker) {
        this.roster = roster;
        this.broker = broker;
    }

    @Scheduled(fixedRateString = "${dalalx.audience.push-ms:1000}")
    public void push() {
        broker.convertAndSend("/topic/leaderboard", new Board(roster.leaderboard(), roster.guestCount()));
    }
}
