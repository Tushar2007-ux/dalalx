package com.dalalx.core.book;

/**
 * One order waiting in the book, as X-Ray shows it: whose it is and how much
 * is left. Immutable.
 *
 * @param orderId      the order's id
 * @param traderId     who placed it
 * @param remainingQty shares still waiting
 */
public record RestingOrder(long orderId, String traderId, long remainingQty) {
}
