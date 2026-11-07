package com.dalalx.core.audience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.dalalx.core.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GuestDeskTest {

    private final MutableClock clock = new MutableClock(Instant.EPOCH);
    private final GuestDesk desk = new GuestDesk(clock, Set.of("The Munim", "You"));

    @Test
    void joiningGivesATraderIdAndASecretToken() {
        Guest richa = desk.join("  Richa  ");

        assertThat(richa.name()).isEqualTo("Richa");
        assertThat(richa.traderId()).isEqualTo("GUEST-1");
        assertThat(Guest.isGuest(richa.traderId())).isTrue();
        assertThat(richa.token()).hasSize(32);
        assertThat(desk.byToken(richa.token())).contains(richa);
        assertThat(desk.byTrader("GUEST-1")).contains(richa);
    }

    @Test
    void aClashingNameGetsANumberInsteadOfBeingRefused() {
        desk.join("Surbhi");

        assertThat(desk.join("surbhi").name()).isEqualTo("surbhi 2");
        assertThat(desk.join("Surbhi").name()).isEqualTo("Surbhi 3");
    }

    @Test
    void badOrReservedNamesAreRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> desk.join("x"));
        assertThatIllegalArgumentException().isThrownBy(() -> desk.join("<script>"));
        assertThatIllegalArgumentException().isThrownBy(() -> desk.join("a".repeat(21)));
        assertThatIllegalArgumentException().isThrownBy(() -> desk.join(null));
        assertThatIllegalArgumentException().isThrownBy(() -> desk.join("the munim"))
                .withMessageContaining("correspondent");
    }

    @Test
    void ordersAreLimitedInSize() {
        Guest g = desk.join("Tushar");

        assertThat(desk.refusal(g, GuestDesk.MAX_QTY)).isNull();
        assertThat(desk.refusal(g, GuestDesk.MAX_QTY + 1)).contains("limited");
        assertThat(desk.refusal(g, 0)).contains("at least 1");
    }

    @Test
    void aBurstIsAllowedThenTheBucketMustRefill() {
        Guest g = desk.join("Tushar");
        for (int i = 0; i < GuestDesk.BURST; i++) {
            assertThat(desk.refusal(g, 1)).isNull();
        }
        assertThat(desk.refusal(g, 1)).contains("orders a second");

        clock.advance(Duration.ofMillis(1000 / GuestDesk.ORDERS_PER_SECOND + 1));

        assertThat(desk.refusal(g, 1)).isNull();
        assertThat(desk.refusal(g, 1)).isNotNull();
    }

    @Test
    void unknownTokensFindNobody() {
        assertThat(desk.byToken("nope")).isEmpty();
        assertThat(desk.byToken(null)).isEmpty();
    }
}
