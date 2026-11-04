package com.dalalx.core.xray;

import com.dalalx.core.book.LevelDetail;
import com.dalalx.core.events.OrderView;
import com.dalalx.core.matching.MatchResult;
import com.dalalx.core.model.OrderRequest;
import java.time.Instant;
import java.util.List;

/**
 * The hook {@code SymbolEngine} calls so orders can be X-Rayed. Capturing the
 * book's full queues costs a little, so the engine first asks whether this
 * order is wanted at all.
 *
 * <p>Called on the symbol's worker thread; implementations must be thread-safe
 * if they are read from elsewhere.
 */
public interface XRayRecorder {

    /** A recorder that never records anything. */
    XRayRecorder NONE = new XRayRecorder() {
        @Override
        public boolean wants(OrderRequest request) {
            return false;
        }

        @Override
        public void record(OrderView arrived, List<LevelDetail> bookBefore, MatchResult result, Instant time) {
        }
    };

    /** @return whether this order should be X-Rayed */
    boolean wants(OrderRequest request);

    /**
     * @param arrived    the order as it arrived (before matching)
     * @param bookBefore the opposite side of the book before matching, best first
     * @param result     what matching did
     * @param time       when it happened
     */
    void record(OrderView arrived, List<LevelDetail> bookBefore, MatchResult result, Instant time);
}
