package com.dalalx.server.config;

import com.dalalx.core.audience.GuestDesk;
import com.dalalx.core.bots.BotRunner;
import com.dalalx.core.bots.TradingBot;
import com.dalalx.server.audience.FloorRoster;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the core {@link GuestDesk} for audience trading. Guests may not take a bot's name or "You". */
@Configuration
public class AudienceConfig {

    @Bean
    public GuestDesk guestDesk(Clock clock, BotRunner bots) {
        Set<String> reserved = new HashSet<>(Set.of(FloorRoster.PRESENTER_NAME, "Presenter", "Admin"));
        bots.bots().stream().map(TradingBot::name).forEach(reserved::add);
        return new GuestDesk(clock, reserved);
    }
}
