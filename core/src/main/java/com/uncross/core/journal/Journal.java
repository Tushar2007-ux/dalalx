package com.uncross.core.journal;

/**
 * Somewhere the exchange writes down every command it carries out.
 *
 * <p>The exchange only knows this interface. Whether entries go to a file
 * ({@link FileJournal}), into a list for a test, or nowhere at all
 * ({@link #NONE}) is decided when the exchange is put together, not inside it.
 *
 * <p><b>Pattern:</b> Strategy for persistence, with {@link #NONE} as a
 * Null Object, so the exchange never has to check "is there a journal?".
 */
@FunctionalInterface
public interface Journal {

    /** Writes nothing: the default, and what tests and the rewind use. */
    Journal NONE = entry -> { };

    /**
     * Records one entry. Called from the symbol workers, so implementations
     * must be safe to call from several threads at once, and quick: a worker
     * must never wait on a disk.
     */
    void record(JournalEntry entry);
}
