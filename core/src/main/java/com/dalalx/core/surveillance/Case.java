package com.dalalx.core.surveillance;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An investigation opened by the Inspector. Immutable: changing its status
 * produces a new {@code Case} (see {@link #withStatus}).
 *
 * @param id        e.g. "SV-0007"
 * @param ruleId    which rule opened it
 * @param ruleName  e.g. "Spoofing"
 * @param severity  how serious
 * @param traderId  the suspect
 * @param symbol    the stock
 * @param openedAt  when the pattern completed
 * @param summary   one line for the alert feed
 * @param figures   key numbers for the case file
 * @param notes     two pencil notes for the timeline
 * @param evidence  the suspect's orders in this stock over the {@value SurveillanceDesk#EVIDENCE_SECONDS} s before
 * @param flagged   ids of the orders forming the pattern
 * @param status    open, dismissed or suspended
 */
public record Case(String id, String ruleId, String ruleName, SurveillanceRule.Severity severity, String traderId,
                   String symbol, Instant openedAt, Instant lastSeenAt, int incidents, String summary,
                   Map<String, String> figures, List<String> notes, List<OrderLife> evidence, List<Long> flagged,
                   Status status) {

    /** Where the investigation stands. */
    public enum Status { OPEN, DISMISSED, SUSPENDED }

    /**
     * A new investigation, opened the first time a trader trips a rule in a stock.
     * Later incidents of the same kind are folded into it by the desk rather than
     * each becoming a case of its own.
     */
    public static Case opened(String id, SurveillanceRule rule, String traderId, String symbol, Instant at,
                              String summary, Map<String, String> figures, List<String> notes,
                              List<OrderLife> evidence, List<Long> flagged) {
        return new Case(id, rule.id(), rule.name(), rule.severity(), traderId, symbol, at, at, 1,
                summary, figures, notes, evidence, flagged, Status.OPEN);
    }

    /** The same investigation after another incident: one more count, the newest evidence. */
    public Case withIncident(Instant at, String summary, Map<String, String> figures, List<String> notes,
                             List<OrderLife> evidence, List<Long> flagged) {
        List<Long> everyFlagged = new java.util.ArrayList<>(this.flagged);
        flagged.stream().filter(id -> !everyFlagged.contains(id)).forEach(everyFlagged::add);
        return new Case(id, ruleId, ruleName, severity, traderId, symbol, openedAt, at, incidents + 1,
                summary, figures, notes, evidence, everyFlagged, status);
    }

    /** @return true if this investigation has seen more than one incident */
    public boolean isRepeat() {
        return incidents > 1;
    }

    public Case {
        figures = Collections.unmodifiableMap(new LinkedHashMap<>(figures)); // keeps display order
        notes = List.copyOf(notes);
        evidence = List.copyOf(evidence);
        flagged = List.copyOf(flagged);
    }

    public Case withStatus(Status newStatus) {
        return new Case(id, ruleId, ruleName, severity, traderId, symbol, openedAt, lastSeenAt, incidents, summary,
                figures, notes, evidence, flagged, newStatus);
    }
}
