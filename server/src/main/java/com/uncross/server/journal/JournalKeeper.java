package com.uncross.server.journal;

import com.uncross.core.accounts.Accounts;
import com.uncross.core.bots.BotRunner;
import com.uncross.core.exchange.Exchange;
import com.uncross.core.journal.FileJournal;
import com.uncross.core.journal.JournalEntry;
import com.uncross.core.journal.JournalFiles;
import com.uncross.core.journal.Recovery;
import com.uncross.core.journal.ReplayClock;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Puts the day back together when the server starts, then keeps the journal
 * open while it runs.
 *
 * <p>Start-up order matters, and this class is where it is decided:
 * <ol>
 *   <li>Spring builds every bean, so every listener is on the event bus.</li>
 *   <li>This class replays the newest session file into the exchange
 *       ({@link Recovery}), so the books, trades, candles, ledgers and accounts
 *       all rebuild from the same events they heard the first time.</li>
 *   <li>Only then is the journal attached (so the replay is not written down a
 *       second time) and the bots let loose.</li>
 * </ol>
 * {@link SmartInitializingSingleton} runs after every bean exists but before
 * the web server opens, so no browser order can slip in half-way.
 *
 * <p>This is wiring, not business logic: every decision about <em>how</em> to
 * replay lives in the core.
 */
public final class JournalKeeper implements SmartInitializingSingleton, DisposableBean {

    /** What the Engine Room shows about the journal. */
    public record Status(boolean enabled, String session, int recoveredEntries, int recoveredOrders, long recoveryMillis) {
    }

    private static final Logger LOG = System.getLogger(JournalKeeper.class.getName());

    private final Exchange exchange;
    private final Accounts accounts;
    private final ReplayClock clock;
    private final BotRunner bots;
    private final boolean enabled;
    private final Path folder;
    private final boolean botsEnabled;
    private FileJournal journal;
    private Status status;

    public JournalKeeper(Exchange exchange, Accounts accounts, ReplayClock clock, BotRunner bots,
                         boolean enabled, Path folder, boolean botsEnabled) {
        this.exchange = exchange;
        this.accounts = accounts;
        this.clock = clock;
        this.bots = bots;
        this.enabled = enabled;
        this.folder = folder;
        this.botsEnabled = botsEnabled;
        this.status = new Status(false, null, 0, 0, 0);
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (enabled) {
            recoverAndOpen();
        }
        if (botsEnabled) {
            bots.start();
        }
    }

    private void recoverAndOpen() {
        Path file = JournalFiles.newest(folder).orElse(folder.resolve(JournalFiles.nameFor(Instant.now())));
        List<JournalEntry> entries = FileJournal.read(file);
        Recovery.Report report = Recovery.replay(entries, exchange, clock, accounts::restore);
        LOG.log(Level.INFO, "Recovered {0} journal entries ({1} orders) from {2} in {3} ms",
                report.entries(), report.orders(), file, report.took().toMillis());
        journal = new FileJournal(file);
        exchange.journalTo(journal);
        accounts.journalTo(journal);
        status = new Status(true, file.getFileName().toString(), report.entries(), report.orders(),
                report.took().toMillis());
    }

    public Status status() {
        return status;
    }

    public Path folder() {
        return folder;
    }

    @Override
    public void destroy() throws InterruptedException {
        if (journal != null) {
            journal.close();
        }
    }
}
