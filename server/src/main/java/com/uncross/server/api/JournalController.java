package com.uncross.server.api;

import com.uncross.core.journal.JournalFiles;
import com.uncross.core.journal.Rewind;
import com.uncross.server.journal.JournalKeeper;
import com.uncross.server.journal.RewindDesk;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The journal and the rewind: what was recovered at start-up, which past
 * sessions are on disk, and the controls for playing one back.
 */
@RestController
@RequestMapping("/api")
public class JournalController {

    public record JournalInfo(JournalKeeper.Status status, List<JournalFiles.Session> sessions) {
    }

    public record StartRewind(String session, Integer speed) {
    }

    public record Speed(int speed) {
    }

    private final JournalKeeper keeper;
    private final RewindDesk rewinds;

    public JournalController(JournalKeeper keeper, RewindDesk rewinds) {
        this.keeper = keeper;
        this.rewinds = rewinds;
    }

    @GetMapping("/journal")
    public JournalInfo journal() {
        return new JournalInfo(keeper.status(), JournalFiles.sessions(keeper.folder()));
    }

    @PostMapping("/rewind")
    public Rewind.View start(@RequestBody StartRewind body) throws InterruptedException {
        return rewinds.start(body.session(), body.speed() == null ? 60 : body.speed());
    }

    @GetMapping("/rewind")
    public Rewind.View view() {
        return current().view();
    }

    @PostMapping("/rewind/pause")
    public Rewind.View pause() {
        Rewind r = current();
        r.pause();
        return r.view();
    }

    @PostMapping("/rewind/resume")
    public Rewind.View resume() {
        Rewind r = current();
        r.resume();
        return r.view();
    }

    @PutMapping("/rewind/speed")
    public Rewind.View speed(@RequestBody Speed body) {
        Rewind r = current();
        r.setSpeed(body.speed());
        return r.view();
    }

    @DeleteMapping("/rewind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop() throws InterruptedException {
        rewinds.stop();
    }

    private Rewind current() {
        return rewinds.current().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "nothing is rewinding"));
    }
}
