package com.uncross.core.journal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The folder of journal files: one file per trading session, named by the
 * moment it began ({@code session-20261117-143000.log}).
 *
 * <p>On start-up the server carries on writing the newest session (after
 * replaying it); the Rewind page lists all of them.
 */
public final class JournalFiles {

    /** One session on disk, for the Rewind page's list. */
    public record Session(String name, long bytes, Instant lastWritten) {
    }

    private static final Pattern NAME = Pattern.compile("session-\\d{8}-\\d{6}\\.log");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private JournalFiles() {
    }

    /** The name a session starting at {@code start} gets. */
    public static String nameFor(Instant start) {
        return "session-" + STAMP.format(start) + ".log";
    }

    /** True for a file name this class wrote; used to refuse paths like {@code ../../etc/passwd}. */
    public static boolean isSessionName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** Every session in {@code folder}, newest first. */
    public static List<Session> sessions(Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> isSessionName(p.getFileName().toString()))
                    .map(JournalFiles::describe)
                    .sorted(Comparator.comparing(Session::name).reversed())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The newest session in {@code folder}, if there is one. */
    public static Optional<Path> newest(Path folder) {
        return sessions(folder).stream().findFirst().map(s -> folder.resolve(s.name()));
    }

    private static Session describe(Path file) {
        try {
            return new Session(file.getFileName().toString(), Files.size(file),
                    Files.getLastModifiedTime(file).toInstant());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
