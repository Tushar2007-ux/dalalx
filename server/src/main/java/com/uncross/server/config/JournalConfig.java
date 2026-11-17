package com.uncross.server.config;

import com.uncross.core.accounts.Accounts;
import com.uncross.core.bots.BotRunner;
import com.uncross.core.exchange.Exchange;
import com.uncross.core.journal.ReplayClock;
import com.uncross.server.journal.JournalKeeper;
import com.uncross.server.journal.RewindDesk;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The journal (recovery after a restart) and the rewind (watching a past day).
 *
 * <p>Both read the folder {@code uncross.journal.dir} (default
 * {@code data/journal}, next to where the server is started). Set
 * {@code uncross.journal.enabled=false} to run with no memory at all, as the
 * tests do.
 */
@Configuration
public class JournalConfig {

    @Bean
    public JournalKeeper journalKeeper(Exchange exchange, Accounts accounts, ReplayClock clock, BotRunner bots,
                                       @Value("${uncross.journal.enabled:true}") boolean enabled,
                                       @Value("${uncross.journal.dir:data/journal}") String dir,
                                       @Value("${uncross.bots.enabled:true}") boolean botsEnabled) {
        return new JournalKeeper(exchange, accounts, clock, bots, enabled, Path.of(dir), botsEnabled);
    }

    @Bean
    public RewindDesk rewindDesk(@Value("${uncross.journal.dir:data/journal}") String dir) {
        return new RewindDesk(Path.of(dir));
    }
}
