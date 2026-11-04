package com.dalalx.core.surveillance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What a rule reports when it sees a pattern: who, where, a one-line summary,
 * the key figures, and which of the trader's orders are the suspicious ones.
 * The desk turns a finding into a {@link Case} with the full evidence.
 *
 * @param traderId  the suspect
 * @param symbol    the stock
 * @param summary   one line, e.g. "10 bids ×9,800–11,200 · 94% cancelled · avg 0.38 s"
 * @param figures   the numbers for the case file, in display order
 * @param flagged   ids of the orders that form the pattern
 * @param notes     two short pencil notes for the timeline
 */
public record Finding(String traderId, String symbol, String summary, Map<String, String> figures,
                      Set<Long> flagged, String[] notes) {

    public Finding {
        figures = Collections.unmodifiableMap(new LinkedHashMap<>(figures)); // keeps display order
        flagged = Set.copyOf(flagged);
        notes = notes.clone();
    }

    @Override
    public String[] notes() {
        return notes.clone();
    }
}
