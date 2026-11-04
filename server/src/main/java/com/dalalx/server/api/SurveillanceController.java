package com.dalalx.server.api;

import com.dalalx.core.surveillance.Case;
import com.dalalx.core.surveillance.SurveillanceDesk;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The Investigations page's API: cases, rules, and the Inspector's two rulings. An adapter only. */
@RestController
@RequestMapping("/api/surveillance")
public class SurveillanceController {

    /** Body of PUT /rules/{id}. */
    public record Switch(boolean enabled) {
    }

    private final SurveillanceDesk desk;

    public SurveillanceController(SurveillanceDesk desk) {
        this.desk = desk;
    }

    @GetMapping("/cases")
    public List<Case> cases() {
        return desk.cases();
    }

    @PostMapping("/cases/{id}/dismiss")
    public Case dismiss(@PathVariable String id) {
        return desk.dismiss(id).orElseThrow(() -> noSuchCase(id));
    }

    /** Suspends the case's trader on every stock and cancels their resting orders. */
    @PostMapping("/cases/{id}/suspend")
    public Case suspend(@PathVariable String id) {
        return desk.suspend(id).orElseThrow(() -> noSuchCase(id));
    }

    @GetMapping("/rules")
    public List<SurveillanceDesk.RuleInfo> rules() {
        return desk.rules();
    }

    @PutMapping("/rules/{id}")
    public List<SurveillanceDesk.RuleInfo> setRule(@PathVariable String id, @RequestBody Switch body) {
        if (!desk.setEnabled(id, body.enabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no rule called " + id);
        }
        return desk.rules();
    }

    @GetMapping("/suspended")
    public Set<String> suspended() {
        return desk.suspensions().all();
    }

    @PostMapping("/traders/{traderId}/reinstate")
    public Set<String> reinstate(@PathVariable String traderId) {
        if (!desk.reinstate(traderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, traderId + " is not suspended");
        }
        return desk.suspensions().all();
    }

    private static ResponseStatusException noSuchCase(String id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "no case " + id);
    }
}
