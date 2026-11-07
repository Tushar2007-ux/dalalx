package com.dalalx.core.bots;

/**
 * One line of a trader's portfolio: how many shares of one stock they hold,
 * what they paid on average, and what that's worth now.
 *
 * <p>All money is in paise. A negative {@code qty} is a short position
 * (sold shares the trader didn't own); its {@code unrealised} profit grows when
 * the price falls.
 *
 * @param symbol     the stock
 * @param qty        shares held (negative = short)
 * @param avgPrice   average price paid (or received, for a short) per share
 * @param lastPrice  latest traded price
 * @param value      {@code qty × lastPrice}
 * @param unrealised profit or loss if the position were closed at {@code lastPrice}
 */
public record Holding(String symbol, long qty, long avgPrice, long lastPrice, long value, long unrealised) {
}
