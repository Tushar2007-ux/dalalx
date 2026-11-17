package com.uncross.core.surveillance;

import static org.assertj.core.api.Assertions.assertThat;

import com.uncross.core.model.Side;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Buying just ahead of someone's block, then selling into it. */
class FrontRunningRuleTest {

    private final Events events = new Events();
    private final FrontRunningRule rule = new FrontRunningRule();
    private long ids = 100;

    private List<Finding> trade(long millis, String buyer, String seller, Side aggressor, long price, long qty) {
        return rule.inspect(events.trade(millis, buyer, seller, aggressor, price, qty, ++ids, ++ids), null);
    }

    /** The Whale lifts 600 shares at a time from 3 s, pushing the price up. */
    private void whaleBlock() {
        for (int i = 0; i < 4; i++) {
            trade(3_000 + i * 500L, "WHALE", "MUNIM", Side.BUY, 10_010 + i * 10L, 600);
        }
    }

    @Test
    void buyingBeforeABlockAndSellingIntoItIsFrontRunning() {
        trade(0, "CLERK", "MUNIM", Side.BUY, 10_000, 500);
        whaleBlock();

        List<Finding> findings = trade(8_000, "WHALE", "CLERK", Side.BUY, 10_050, 500);

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.traderId()).isEqualTo("CLERK");
            assertThat(f.figures()).containsEntry("Block by", "WHALE").containsKey("Gain per share");
            assertThat(f.summary()).contains("ahead of WHALE");
        });
    }

    @Test
    void buyingOnlyAfterTheBlockStartedIsJustFollowingTheCrowd() {
        whaleBlock();
        trade(5_000, "CLERK", "MUNIM", Side.BUY, 10_040, 500);

        assertThat(trade(8_000, "WHALE", "CLERK", Side.BUY, 10_050, 500)).isEmpty();
    }

    @Test
    void sellingAtALossIsNotFrontRunning() {
        trade(0, "CLERK", "MUNIM", Side.BUY, 10_100, 500);
        whaleBlock();

        assertThat(trade(8_000, "WHALE", "CLERK", Side.BUY, 10_050, 500)).isEmpty();
    }

    @Test
    void anOrdinaryBusyTapeHasNoBlockAndNoCase() {
        trade(0, "CLERK", "MUNIM", Side.BUY, 10_000, 500);
        trade(3_000, "MOMENTUM", "MUNIM", Side.BUY, 10_010, 300); // well under a block
        assertThat(trade(5_000, "MOMENTUM", "CLERK", Side.BUY, 10_050, 500)).isEmpty();
    }

    @Test
    void theStandardDeskIncludesTheRule() {
        assertThat(SurveillanceDesk.standardRules()).extracting(SurveillanceRule::id).contains("front-running");
    }
}
