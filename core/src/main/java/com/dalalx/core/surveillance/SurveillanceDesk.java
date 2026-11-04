package com.dalalx.core.surveillance;

import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.events.EngineListener;
import com.dalalx.core.events.OrderAccepted;
import com.dalalx.core.events.OrderCancelled;
import com.dalalx.core.events.TradeExecuted;
import com.dalalx.core.surveillance.Case.Status;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The Surveillance Inspector's desk: watches every engine event (Observer),
 * runs each enabled {@link SurveillanceRule} over it, and turns findings into
 * numbered {@link Case}s with evidence. The human (or the demo) then dismisses
 * a case or suspends the trader, which bars their orders and cancels whatever
 * they have resting.
 *
 * <p><b>Thread safety:</b> events arrive on five worker threads and web
 * requests read and act on cases, so all state is guarded by this object's
 * lock. Listeners (headlines, the WebSocket bridge) are called outside it.
 */
public final class SurveillanceDesk implements EngineListener {

    static final long EVIDENCE_SECONDS = 30;
    static final int MAX_CASES = 200;

    /** Told about new cases and status changes. */
    public interface CaseListener {
        void caseChanged(Case c);
    }

    /** A rule with its on/off switch, for the rules panel. */
    public record RuleInfo(String id, String name, String threshold, SurveillanceRule.Severity severity, boolean enabled) {
    }

    private final Map<String, SurveillanceRule> rules = new LinkedHashMap<>();
    private final Map<String, Boolean> enabled = new LinkedHashMap<>();
    private final ActivityLog log = new ActivityLog();
    private final Deque<Case> cases = new ArrayDeque<>();
    private final Suspensions suspensions;
    private final Consumer<String> cancelAll;
    private final List<CaseListener> listeners = new CopyOnWriteArrayList<>();
    private int nextCase;

    /**
     * @param rules       the patterns to look for
     * @param suspensions the list of barred traders this desk maintains
     * @param cancelAll   cancels every resting order of a trader (the exchange provides it)
     */
    public SurveillanceDesk(List<SurveillanceRule> rules, Suspensions suspensions, Consumer<String> cancelAll) {
        rules.forEach(r -> {
            this.rules.put(r.id(), r);
            this.enabled.put(r.id(), true);
        });
        this.suspensions = Objects.requireNonNull(suspensions, "suspensions must not be null");
        this.cancelAll = Objects.requireNonNull(cancelAll, "cancelAll must not be null");
    }

    /** @return the four standard rules */
    public static List<SurveillanceRule> standardRules() {
        return List.of(new SpoofingRule(), new LayeringRule(), new WashTradeRule(), new PumpRule());
    }

    public void addListener(CaseListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    @Override
    public void onEvent(EngineEvent event) {
        List<Case> opened = inspect(event);
        opened.forEach(c -> listeners.forEach(l -> l.caseChanged(c)));
    }

    private synchronized List<Case> inspect(EngineEvent event) {
        switch (event) {
            case OrderAccepted e -> log.placed(e);
            case OrderCancelled e -> log.cancelled(e);
            case TradeExecuted e -> log.traded(e.trade());
            default -> {
                return List.of(); // other events can't complete a pattern
            }
        }
        List<Case> opened = new ArrayList<>();
        for (SurveillanceRule rule : rules.values()) {
            if (!enabled.get(rule.id())) {
                continue;
            }
            for (Finding f : rule.inspect(event, log)) {
                if (!suspensions.isSuspended(f.traderId())) {
                    opened.add(open(rule, f, event.timestamp()));
                }
            }
        }
        return opened;
    }

    private Case open(SurveillanceRule rule, Finding f, Instant at) {
        List<OrderLife> evidence = log.of(f.traderId(), f.symbol(), at.minus(Duration.ofSeconds(EVIDENCE_SECONDS)));
        Case c = new Case("SV-%04d".formatted(++nextCase), rule.id(), rule.name(), rule.severity(), f.traderId(),
                f.symbol(), at, f.summary(), f.figures(), List.of(f.notes()), evidence, List.copyOf(f.flagged()), Status.OPEN);
        cases.addFirst(c);
        if (cases.size() > MAX_CASES) {
            cases.removeLast();
        }
        return c;
    }

    /** @return every case, newest first */
    public synchronized List<Case> cases() {
        return List.copyOf(cases);
    }

    public synchronized Optional<Case> find(String caseId) {
        return cases.stream().filter(c -> c.id().equals(caseId)).findFirst();
    }

    /** @return open cases against this trader (used to flag them in the book) */
    public synchronized List<Case> openCasesAgainst(String traderId) {
        return cases.stream().filter(c -> c.status() == Status.OPEN && c.traderId().equals(traderId)).toList();
    }

    /** Closes a case with no action. */
    public Optional<Case> dismiss(String caseId) {
        return change(caseId, Status.DISMISSED);
    }

    /**
     * Suspends the case's trader: their new orders are refused on every stock
     * and everything they have resting is cancelled. Other open cases against
     * the same trader are marked suspended too.
     */
    public Optional<Case> suspend(String caseId) {
        Optional<Case> changed = change(caseId, Status.SUSPENDED);
        changed.ifPresent(c -> {
            suspensions.suspend(c.traderId());
            List<Case> others;
            synchronized (this) {
                others = openCasesAgainst(c.traderId()).stream().map(o -> replace(o, Status.SUSPENDED)).toList();
            }
            cancelAll.accept(c.traderId());
            others.forEach(o -> listeners.forEach(l -> l.caseChanged(o)));
        });
        return changed;
    }

    /** Lets a suspended trader trade again. @return false if they weren't suspended */
    public boolean reinstate(String traderId) {
        return suspensions.reinstate(traderId);
    }

    public Suspensions suspensions() {
        return suspensions;
    }

    private Optional<Case> change(String caseId, Status status) {
        Optional<Case> updated;
        synchronized (this) {
            updated = find(caseId).map(c -> replace(c, status));
        }
        updated.ifPresent(c -> listeners.forEach(l -> l.caseChanged(c)));
        return updated;
    }

    /** Swaps a case for its new version, keeping its place in the list. Caller holds the lock. */
    private Case replace(Case old, Status status) {
        Case updated = old.withStatus(status);
        List<Case> all = new ArrayList<>(cases);
        all.set(all.indexOf(old), updated);
        cases.clear();
        cases.addAll(all);
        return updated;
    }

    /** @return each rule with its switch */
    public synchronized List<RuleInfo> rules() {
        return rules.values().stream()
                .map(r -> new RuleInfo(r.id(), r.name(), r.threshold(), r.severity(), enabled.get(r.id()))).toList();
    }

    /** Turns a rule on or off. @return false if there is no such rule */
    public synchronized boolean setEnabled(String ruleId, boolean on) {
        if (!rules.containsKey(ruleId)) {
            return false;
        }
        enabled.put(ruleId, on);
        return true;
    }
}
