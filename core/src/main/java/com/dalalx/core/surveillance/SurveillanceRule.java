package com.dalalx.core.surveillance;

import com.dalalx.core.events.EngineEvent;
import java.util.List;

/**
 * One market-abuse pattern the Inspector looks for: the <b>Strategy</b> and
 * <b>Open/Closed</b> patterns again. The desk feeds every engine event to every
 * enabled rule; each rule keeps whatever sliding-window state it needs and
 * reports findings. A new kind of abuse is a new class, with no change to the
 * desk or to the other rules.
 */
public interface SurveillanceRule {

    /** How serious a finding from this rule is, for the alert feed. */
    enum Severity { NOTICE, WARNING, CRITICAL }

    /** @return a stable id, e.g. {@code "spoofing"} */
    String id();

    /** @return the display name, e.g. "Spoofing" */
    String name();

    /** @return the threshold in words, e.g. "Cancel ratio > 85% in 30 s on orders ≥ 5,000" */
    String threshold();

    Severity severity();

    /**
     * Looks at one event (with the activity log already updated for it).
     *
     * @return any patterns this event completes (usually none)
     */
    List<Finding> inspect(EngineEvent event, ActivityView activity);
}
