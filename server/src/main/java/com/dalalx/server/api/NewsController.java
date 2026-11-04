package com.dalalx.server.api;

import com.dalalx.core.headlines.Headline;
import com.dalalx.core.headlines.HeadlineWriter;
import com.dalalx.core.scenarios.Scenario;
import com.dalalx.core.scenarios.ScenarioDirector;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The Wire (latest headlines) and the storylines that can be run. An adapter only. */
@RestController
@RequestMapping("/api")
public class NewsController {

    /** A storyline as the Correspondents page shows it. */
    public record StoryInfo(String id, String kicker, String headline, String deck, String symbol,
                            Scenario.Tone tone, long durationSeconds) {
        static StoryInfo of(Scenario s) {
            return new StoryInfo(s.id(), s.kicker(), s.headline(), s.deck(), s.symbol(), s.tone(),
                    s.duration().toSeconds());
        }
    }

    /** What is running now; all fields null when nothing is. */
    public record RunningStory(String id, Instant startedAt, Long durationSeconds) {
        static RunningStory of(ScenarioDirector.Status s) {
            return s.running()
                    ? new RunningStory(s.scenarioId(), s.startedAt(), s.duration().toSeconds())
                    : new RunningStory(null, null, null);
        }
    }

    /** The storyline catalogue plus what is running now. */
    public record Storylines(List<StoryInfo> stories, RunningStory running) {
    }

    private final HeadlineWriter writer;
    private final ScenarioDirector director;

    public NewsController(HeadlineWriter writer, ScenarioDirector director) {
        this.writer = writer;
        this.director = director;
    }

    @GetMapping("/wire")
    public List<Headline> wire(@RequestParam(defaultValue = "20") int limit) {
        if (limit < 1 || limit > HeadlineWriter.CAPACITY) {
            throw new IllegalArgumentException("limit must be 1–" + HeadlineWriter.CAPACITY);
        }
        return writer.latest(limit);
    }

    @GetMapping("/scenarios")
    public Storylines scenarios() {
        return new Storylines(director.scenarios().stream().map(StoryInfo::of).toList(),
                RunningStory.of(director.status()));
    }

    /** Queues the story; it begins on the bot thread's next tick (a quarter of a second). */
    @PostMapping("/scenarios/{id}/start")
    public StoryInfo start(@PathVariable String id) {
        if (!director.start(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no storyline called " + id);
        }
        return StoryInfo.of(director.scenario(id).orElseThrow());
    }

    @PostMapping("/scenarios/stop")
    public void stop() {
        director.stop();
    }
}
