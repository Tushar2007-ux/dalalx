package com.dalalx.core.marketdata;

/**
 * One bar of a price chart: what happened to a stock's price during one interval.
 *
 * <p>OHLCV: the <b>o</b>pen (first trade price), <b>h</b>igh, <b>l</b>ow and
 * <b>c</b>lose (last trade price), plus the <b>v</b>olume (shares traded). All
 * prices are in paise.
 *
 * @param startMillis when the interval began (epoch milliseconds)
 * @param open        first trade price
 * @param high        highest trade price
 * @param low         lowest trade price
 * @param close       last trade price
 * @param volume      total shares traded
 */
public record Candle(long startMillis, long open, long high, long low, long close, long volume) {

    /** @return a new candle for a single trade */
    static Candle first(long startMillis, long price, long qty) {
        return new Candle(startMillis, price, price, price, price, qty);
    }

    /** @return this candle updated with one more trade (records are immutable, so a new one) */
    Candle with(long price, long qty) {
        return new Candle(startMillis, open, Math.max(high, price), Math.min(low, price), price,
                Math.addExact(volume, qty));
    }
}
