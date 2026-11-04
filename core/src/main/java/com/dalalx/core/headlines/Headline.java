package com.dalalx.core.headlines;

import java.time.Instant;

/**
 * One item on The Wire: a short, plain-English sentence about something the
 * market just did. Immutable.
 *
 * @param id       increasing number, newest is highest
 * @param time     when it happened
 * @param kicker   the small label above it ("Markets", "Stop Press"...)
 * @param symbol   the stock it is about, or {@code null} for market-wide news
 * @param text     the headline itself
 * @param deck     an optional second sentence, or {@code null}
 * @param tone     which way the news points
 */
public record Headline(long id, Instant time, String kicker, String symbol, String text, String deck, Tone tone) {

    /** Up (green ▲), down (red ▼), alert (stop-press red) or neutral. */
    public enum Tone { UP, DOWN, ALERT, NEUTRAL }
}
