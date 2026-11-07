import type { Trade } from '../lib/types'

/** Trades worth at least ₹10 lakh (or 1,000 shares) are "block trades" and pulse on the tape. */
export const BLOCK_VALUE_PAISE = 10_00_000 * 100
export const BLOCK_QTY = 1_000

export function isBlockTrade(t: Pick<Trade, 'price' | 'qty'>): boolean {
  return t.qty >= BLOCK_QTY || t.price * t.qty >= BLOCK_VALUE_PAISE
}
