package com.uncross.core.bots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A timed round: who makes the most money from the starting whistle? */
class TournamentTest {

    /** A clock the test moves by hand. */
    private static final class HandClock extends Clock {
        Instant now = Instant.parse("2026-11-17T10:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final HandClock clock = new HandClock();
    private final Map<String, Long> pnl = new HashMap<>(Map.of("MUNIM", 5_000L, "WHALE", -2_000L, "PANIC", 0L));
    private final BotRunner runner = new BotRunner(List.of(new MarketMakerBot(), new WhaleBot(), new PanicSellerBot()),
            new FakeMarket().with("TCS", 1), new EngineGateway(), new ZeroRandom(), 100);
    private final Tournament tournament = new Tournament(runner, id -> pnl.getOrDefault(id, 0L), clock);

    @Test
    void scoresCountOnlyWhatIsMadeAfterTheStart() {
        tournament.start(Duration.ofMinutes(2));
        pnl.put("WHALE", 8_000L);   // +10,000 since the start
        pnl.put("MUNIM", 6_000L);   // +1,000

        assertThat(tournament.standings().scores()).extracting(Tournament.Score::traderId, Tournament.Score::pnl)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("WHALE", 10_000L),
                        org.assertj.core.groups.Tuple.tuple("MUNIM", 1_000L),
                        org.assertj.core.groups.Tuple.tuple("PANIC", 0L));
    }

    @Test
    void whenTimeIsUpTheTableFreezesAndTheWinnerIsAnnounced() {
        List<Tournament.Standings> announced = new ArrayList<>();
        tournament.onFinish(announced::add);
        tournament.start(Duration.ofMinutes(1));
        pnl.put("PANIC", 3_000L);

        clock.now = clock.now.plusSeconds(61);
        tournament.onTick(null);
        pnl.put("MUNIM", 1_000_000L); // after the whistle: does not count

        Tournament.Standings result = tournament.standings();
        assertThat(result.state()).isEqualTo(Tournament.State.FINISHED);
        assertThat(result.leader()).get().extracting(Tournament.Score::name).isEqualTo("Panic Seller");
        assertThat(announced).singleElement().isEqualTo(result);

        tournament.onTick(null);
        assertThat(announced).hasSize(1); // announced once only
    }

    @Test
    void aRoundHasSensibleLength() {
        assertThatIllegalArgumentException().isThrownBy(() -> tournament.start(Duration.ofSeconds(5)));
        assertThat(tournament.standings().state()).isEqualTo(Tournament.State.IDLE);
    }
}
