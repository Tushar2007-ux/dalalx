package com.dalalx.core.surveillance;

import static org.assertj.core.api.Assertions.assertThat;

import com.dalalx.core.events.EngineEvent;
import com.dalalx.core.model.Side;
import com.dalalx.core.surveillance.Case.Status;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SurveillanceRulesTest {

    private final Events ev = new Events();
    private final List<String> cancelledFor = new ArrayList<>();
    private final Suspensions suspensions = new Suspensions();
    private final SurveillanceDesk desk = new SurveillanceDesk(SurveillanceDesk.standardRules(), suspensions, cancelledFor::add);

    private void feed(EngineEvent... events) {
        for (EngineEvent e : events) {
            desk.onEvent(e);
        }
    }

    private List<String> rulesHit() {
        return desk.cases().stream().map(Case::ruleId).toList();
    }

    /** Three big bids on neighbouring ticks, each pulled 300 ms later. */
    private void spoofCycle(long start, long firstId) {
        for (int i = 0; i < 3; i++) {
            feed(ev.placed(start, firstId + i, "SPOOFER", Side.BUY, 245_000 - i * 5, 9_800));
        }
        for (int i = 0; i < 3; i++) {
            feed(ev.pulled(start + 300, firstId + i, "SPOOFER", Side.BUY, 245_000 - i * 5, 9_800));
        }
    }

    @Test
    void largeOrdersPulledAlmostAtOnceAreSpoofingAndLayering() {
        spoofCycle(0, 1);

        assertThat(rulesHit()).containsExactlyInAnyOrder("spoofing", "layering");
        Case spoof = desk.cases().stream().filter(c -> c.ruleId().equals("spoofing")).findFirst().orElseThrow();
        assertThat(spoof.id()).startsWith("SV-");
        assertThat(spoof.traderId()).isEqualTo("SPOOFER");
        assertThat(spoof.figures()).containsEntry("Cancel ratio", "100%").containsEntry("Avg time to cancel", "0.30 s");
        assertThat(spoof.evidence()).hasSize(3);
        assertThat(spoof.flagged()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void smallOrdersOrSlowCancelsAreNotAbuse() {
        for (int i = 0; i < 5; i++) {
            feed(ev.placed(i * 100, 10 + i, "MUNIM", Side.BUY, 245_000 - i * 100, 300));    // small, ₹1 apart
            feed(ev.pulled(i * 100 + 200, 10 + i, "MUNIM", Side.BUY, 245_000 - i * 100, 300));
        }
        for (int i = 0; i < 3; i++) {
            feed(ev.placed(5_000, 20 + i, "WHALE", Side.BUY, 240_000 - i * 5, 6_000));       // large but...
            feed(ev.pulled(9_000, 20 + i, "WHALE", Side.BUY, 240_000 - i * 5, 6_000));       // ...4 s later: not layering
        }
        assertThat(rulesHit()).containsExactly("spoofing"); // the whale's 100% cancel of 3 large orders is still spoofing
        assertThat(desk.cases().get(0).traderId()).isEqualTo("WHALE");
    }

    @Test
    void aMarketMakerReplacingOneQuoteAtATimeIsNotLayering() {
        for (int i = 0; i < 5; i++) {   // place, pull, place the next a tick lower...
            feed(ev.placed(i * 400, 40 + i, "MUNIM", Side.BUY, 245_000 - i * 5, 200));
            feed(ev.pulled(i * 400 + 300, 40 + i, "MUNIM", Side.BUY, 245_000 - i * 5, 200));
        }
        assertThat(rulesHit()).isEmpty();
    }

    @Test
    void layeringNeedsThePricesToBeClose() {
        for (int i = 0; i < 3; i++) {
            feed(ev.placed(0, 30 + i, "X", Side.SELL, 246_000 + i * 100, 100)); // ₹1 apart: 20 ticks
            feed(ev.pulled(200, 30 + i, "X", Side.SELL, 246_000 + i * 100, 100));
        }
        assertThat(rulesHit()).isEmpty();
    }

    @Test
    void tradingWithYourselfIsAWashTrade() {
        feed(ev.trade(0, "WHALE", "WHALE", Side.BUY, 168_300, 5_000, 1, 2));
        assertThat(rulesHit()).isEmpty(); // one pair could be an accident
        feed(ev.trade(300, "WHALE", "WHALE", Side.SELL, 168_325, 5_000, 3, 4));

        assertThat(rulesHit()).containsExactly("wash");
        assertThat(desk.cases().get(0).figures()).containsEntry("Matched pairs", "2").containsEntry("Volume created", "10,000");
    }

    @Test
    void aQuickRoundTripAtOnePriceIsAWashPair() {
        feed(ev.trade(0, "WHALE", "MUNIM", Side.BUY, 168_300, 5_000, 1, 2),
             ev.trade(200, "PANIC", "WHALE", Side.SELL, 168_300, 5_000, 3, 4),   // pair 1, 0.2 s apart
             ev.trade(1_000, "WHALE", "MUNIM", Side.BUY, 168_300, 5_000, 5, 6),
             ev.trade(1_300, "PANIC", "WHALE", Side.SELL, 168_300, 5_000, 7, 8));
        assertThat(rulesHit()).containsExactly("wash");
    }

    @Test
    void oneTraderDrivingThePriceUpIsAPump() {
        for (int i = 0; i < 8; i++) {
            feed(ev.trade(i * 1_000, "MOMENTUM", "MUNIM", Side.BUY, 154_200 + i * 120, 400, 100 + i, 200 + i));
        }
        assertThat(rulesHit()).containsExactly("pump");
        assertThat(desk.cases().get(0).traderId()).isEqualTo("MOMENTUM");
        assertThat(desk.cases().get(0).figures()).containsEntry("Share of buying", "100%");
    }

    @Test
    void aRiseSpreadAcrossManyBuyersIsNotAPump() {
        String[] buyers = {"A", "B", "C", "D", "E", "F", "G", "H"};
        for (int i = 0; i < 8; i++) {
            feed(ev.trade(i * 1_000, buyers[i], "MUNIM", Side.BUY, 154_200 + i * 200, 400, 100 + i, 200 + i));
        }
        assertThat(rulesHit()).isEmpty();
    }

    @Test
    void suspendingCancelsTheTradersOrdersAndClosesTheirOtherCases() {
        spoofCycle(0, 1);
        String first = desk.cases().get(0).id();

        desk.suspend(first);

        assertThat(suspensions.isSuspended("SPOOFER")).isTrue();
        assertThat(cancelledFor).containsExactly("SPOOFER");
        assertThat(desk.cases()).extracting(Case::status).containsOnly(Status.SUSPENDED);
        assertThat(suspensions.refusal("SPOOFER")).hasValue("trader suspended by market surveillance");
        assertThat(desk.reinstate("SPOOFER")).isTrue();
        assertThat(suspensions.isSuspended("SPOOFER")).isFalse();
    }

    @Test
    void dismissedCasesStayOnFileAndSwitchedOffRulesAreSilent() {
        desk.setEnabled("layering", false);
        spoofCycle(0, 1);
        assertThat(rulesHit()).containsExactly("spoofing");

        Case dismissed = desk.dismiss(desk.cases().get(0).id()).orElseThrow();
        assertThat(dismissed.status()).isEqualTo(Status.DISMISSED);
        assertThat(desk.rules()).filteredOn(r -> r.id().equals("layering")).extracting(r -> r.enabled()).containsExactly(false);
    }
}
