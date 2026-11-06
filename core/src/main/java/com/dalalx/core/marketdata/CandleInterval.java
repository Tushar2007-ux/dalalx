package com.dalalx.core.marketdata;

import java.time.Duration;
import java.util.Arrays;

/**
 * The bar sizes the price chart can show.
 *
 * <p>Each constant carries its own length and the short code the browser uses
 * ({@code "1m"}, {@code "5m"}, ...), so converting between them lives in one
 * place instead of being scattered through the code as string comparisons.
 */
public enum CandleInterval {
    /** Ten-second candles: a lively chart in the first minutes of a session. */
    TEN_SECONDS("10s", Duration.ofSeconds(10)),
    ONE_MINUTE("1m", Duration.ofMinutes(1)),
    FIVE_MINUTES("5m", Duration.ofMinutes(5)),
    FIFTEEN_MINUTES("15m", Duration.ofMinutes(15)),
    ONE_DAY("1D", Duration.ofDays(1));

    private final String code;
    private final Duration length;

    CandleInterval(String code, Duration length) {
        this.code = code;
        this.length = length;
    }

    /** @return the short code, e.g. {@code "5m"} */
    public String code() {
        return code;
    }

    /** @return how long one candle lasts */
    public Duration length() {
        return length;
    }

    /** @return the start (epoch millis) of the candle containing {@code epochMillis} */
    public long bucketStart(long epochMillis) {
        long size = length.toMillis();
        return Math.floorDiv(epochMillis, size) * size;
    }

    /**
     * @param code e.g. {@code "1m"}
     * @throws IllegalArgumentException for an unknown code
     */
    public static CandleInterval fromCode(String code) {
        return Arrays.stream(values()).filter(i -> i.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown interval '" + code + "'; use 1m, 5m, 15m or 1D"));
    }
}
