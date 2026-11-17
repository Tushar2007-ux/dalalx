package com.uncross.server.journal;

import com.uncross.core.exchange.Exchange;
import com.uncross.core.journal.FileJournal;
import com.uncross.core.journal.JournalFiles;
import com.uncross.core.journal.Rewind;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.DisposableBean;

/**
 * Holds the one rewind that may be playing at a time, so the web can start,
 * pause and stop it. Starting a new one stops the old one first.
 */
public final class RewindDesk implements DisposableBean {

    private final Path folder;
    private Rewind current;

    public RewindDesk(Path folder) {
        this.folder = folder;
    }

    public synchronized Rewind.View start(String session, int speed) throws InterruptedException {
        if (!JournalFiles.isSessionName(session)) {
            throw new IllegalArgumentException("no session called " + session);
        }
        Path file = folder.resolve(session);
        if (!file.toFile().isFile()) {
            throw new IllegalArgumentException("no session called " + session);
        }
        stop();
        current = new Rewind(session, FileJournal.read(file), Exchange.DEFAULT_STOCKS, speed).start();
        return current.view();
    }

    public synchronized Optional<Rewind> current() {
        return Optional.ofNullable(current);
    }

    public synchronized void stop() throws InterruptedException {
        if (current != null) {
            current.close();
            current = null;
        }
    }

    @Override
    public void destroy() throws InterruptedException {
        stop();
    }
}
