package com.uncross.server.journal;

import static org.assertj.core.api.Assertions.assertThat;

import com.uncross.core.accounts.Accounts;
import com.uncross.core.book.BookSnapshot;
import com.uncross.core.exchange.Exchange;
import com.uncross.core.journal.JournalCodec;
import com.uncross.core.journal.JournalEntry;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A server that starts with yesterday's journal on disk comes up with
 * yesterday's books, and keeps writing to the same file.
 */
@SpringBootTest
@DirtiesContext
class JournalRecoveryTest {

    private static final Path FOLDER;
    private static final Path SESSION;

    static {
        try {
            FOLDER = Files.createTempDirectory("uncross-journal");
            SESSION = FOLDER.resolve("session-20261117-091500.log");
            Instant t = Instant.parse("2026-11-17T09:15:00Z");
            List<JournalEntry> day = List.of(
                    new JournalEntry.AccountOpened(t, "Chetan", "00:ff"),
                    new JournalEntry.Submitted(t, 5, new OrderRequest("TCS", "MUNIM", Side.SELL, OrderType.LIMIT, 401_000, 70)),
                    new JournalEntry.Submitted(t.plusSeconds(1), 9, new OrderRequest("TCS", "MUNIM", Side.SELL, OrderType.LIMIT, 402_000, 30)),
                    new JournalEntry.Cancelled(t.plusSeconds(2), 9));
            Files.write(SESSION, day.stream().map(JournalCodec::encode).toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void journal(DynamicPropertyRegistry registry) {
        registry.add("uncross.journal.enabled", () -> "true");
        registry.add("uncross.journal.dir", FOLDER::toString);
    }

    @Autowired
    private Exchange exchange;
    @Autowired
    private Accounts accounts;
    @Autowired
    private JournalKeeper keeper;

    @Test
    void theBooksAndAccountsComeBackAndNewOrdersAreJournalled() throws Exception {
        BookSnapshot tcs = exchange.snapshot("TCS", 5).join();
        assertThat(tcs.asks()).singleElement().satisfies(level -> {
            assertThat(level.price()).isEqualTo(401_000);
            assertThat(level.totalQty()).isEqualTo(70);
        });
        assertThat(accounts.byTrader("A-CHETAN")).isPresent();
        assertThat(keeper.status().recoveredEntries()).isEqualTo(4);
        assertThat(keeper.status().session()).isEqualTo(SESSION.getFileName().toString());

        exchange.submit(new OrderRequest("INFY", "YOU", Side.BUY, OrderType.LIMIT, 150_000, 1)).join();
        long deadline = System.currentTimeMillis() + 3_000;
        while (Files.readAllLines(SESSION).size() < 5 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(Files.readAllLines(SESSION)).hasSize(5).last().asString().contains("|SUBMIT|").contains("|INFY|YOU|");
    }
}
