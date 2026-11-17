package com.uncross.core.journal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A {@link Journal} kept in a text file, one {@link JournalCodec} line per
 * entry, appended as the day goes on.
 *
 * <p><b>Concurrency:</b> five symbol workers record entries at once, and none
 * of them may wait for the disk. So recording only puts the line on a
 * {@link BlockingQueue}; one writer thread, the only thread that ever touches
 * the file, takes lines off the queue and writes them. It is the same idea as
 * the symbol workers themselves: give one thread sole ownership and let
 * everyone else hand it work. The writer flushes whenever the queue runs dry,
 * so a line reaches the disk within moments of the command.
 *
 * <p>What survives a crash is everything flushed before it: possibly not the
 * last few milliseconds. A real exchange would wait for the disk before
 * confirming each order; that trade-off (speed against safety) is worth
 * naming in a viva.
 */
public final class FileJournal implements Journal, AutoCloseable {

    private static final Logger LOG = System.getLogger(FileJournal.class.getName());
    private static final String STOP = "\u0000stop";

    private final Path file;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private final Thread writer;
    private volatile boolean open = true;

    /** Opens {@code file} for appending (creating it and its folder if needed) and starts the writer. */
    public FileJournal(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create journal folder for " + file, e);
        }
        this.writer = new Thread(this::writeLoop, "journal-writer");
        writer.setDaemon(true);
        writer.start();
    }

    @Override
    public void record(JournalEntry entry) {
        if (open) {
            lines.add(JournalCodec.encode(entry));
        }
    }

    private void writeLoop() {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            while (true) {
                String line = lines.take();
                if (STOP.equals(line)) {
                    out.flush();
                    return;
                }
                out.write(line);
                out.newLine();
                if (lines.isEmpty()) {
                    out.flush(); // caught up: get it onto the disk now
                }
            }
        } catch (IOException e) {
            LOG.log(Level.ERROR, "journal " + file + " stopped writing", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Writes out everything queued so far, then stops the writer. */
    @Override
    public void close() throws InterruptedException {
        open = false;
        lines.add(STOP);
        writer.join(TimeUnit.SECONDS.toMillis(5));
    }

    public Path file() {
        return file;
    }

    /** Reads every readable entry of a journal file, in the order written. Damaged lines are skipped. */
    public static List<JournalEntry> read(Path file) {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<JournalEntry> entries = new ArrayList<>();
        try (Stream<String> all = Files.lines(file, StandardCharsets.UTF_8)) {
            all.forEach(line -> JournalCodec.decode(line).ifPresent(entries::add));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read journal " + file, e);
        }
        return entries;
    }
}
