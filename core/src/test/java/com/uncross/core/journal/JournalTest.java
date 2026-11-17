package com.uncross.core.journal;

import static org.assertj.core.api.Assertions.assertThat;

import com.uncross.core.exchange.ListedStock;
import com.uncross.core.journal.JournalEntry.AccountOpened;
import com.uncross.core.journal.JournalEntry.Amended;
import com.uncross.core.journal.JournalEntry.Cancelled;
import com.uncross.core.journal.JournalEntry.CancelledAll;
import com.uncross.core.journal.JournalEntry.Listed;
import com.uncross.core.journal.JournalEntry.Submitted;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The journal's file format, and writing and reading a file. */
class JournalTest {

    private static final Instant T = Instant.parse("2026-11-17T09:15:00.123Z");

    @Test
    void everyKindOfEntrySurvivesARoundTrip() {
        List<JournalEntry> entries = List.of(
                new Submitted(T, 7, new OrderRequest("TCS", "YOU", Side.BUY, OrderType.ICEBERG, 409_000, 500, 100)),
                new Submitted(T, 8, new OrderRequest("TCS", "YOU", Side.SELL, OrderType.MARKET, 0, 5)),
                new Cancelled(T, 7),
                new Amended(T, 8, 410_000, 40, 12),
                new CancelledAll(T, "SPOOFER"),
                new Listed(T, new ListedStock("WIPRO", "Wipro | (simulated) 100%", 25_000)),
                new AccountOpened(T, "Priya S", "ab12:cd34"));

        for (JournalEntry entry : entries) {
            assertThat(JournalCodec.decode(JournalCodec.encode(entry))).contains(entry);
        }
    }

    @Test
    void aLineIsPlainTextAPersonCanRead() {
        String line = JournalCodec.encode(new Cancelled(T, 42));
        assertThat(line).isEqualTo("2026-11-17T09:15:00.123Z|CANCEL|42");
    }

    @Test
    void aDamagedLineIsSkippedNotFatal() {
        assertThat(JournalCodec.decode("2026-11-17T09:15:00Z|SUBMIT|7|TCS")).isEmpty();
        assertThat(JournalCodec.decode("garbage")).isEmpty();
        assertThat(JournalCodec.decode("2026-11-17T09:15:00Z|LAUNCH|1")).isEmpty();
    }

    @Test
    void theFileJournalWritesLinesThatReadBackInOrder(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("deep/session.log");
        try (FileJournal journal = new FileJournal(file)) {
            journal.record(new Cancelled(T, 1));
            journal.record(new Cancelled(T, 2));
        }
        Files.writeString(file, "half a line with no end", java.nio.file.StandardOpenOption.APPEND);

        assertThat(FileJournal.read(file)).extracting(e -> ((Cancelled) e).orderId()).containsExactly(1L, 2L);
        assertThat(FileJournal.read(dir.resolve("missing.log"))).isEmpty();
    }

    @Test
    void sessionsAreNamedByTheirStartAndListedNewestFirst(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(JournalFiles.nameFor(Instant.parse("2026-11-16T10:00:00Z"))), "");
        Files.writeString(dir.resolve(JournalFiles.nameFor(Instant.parse("2026-11-17T10:00:00Z"))), "");
        Files.writeString(dir.resolve("notes.txt"), "");

        assertThat(JournalFiles.sessions(dir)).extracting(JournalFiles.Session::name)
                .containsExactly("session-20261117-100000.log", "session-20261116-100000.log");
        assertThat(JournalFiles.isSessionName("../../etc/passwd")).isFalse();
    }

    @Test
    void thePinnedClockTellsTheJournalsTimeUntilReleased() {
        ReplayClock clock = new ReplayClock(Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC));
        clock.pin(T);
        assertThat(clock.instant()).isEqualTo(T);
        clock.release();
        assertThat(clock.instant()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
    }
}
