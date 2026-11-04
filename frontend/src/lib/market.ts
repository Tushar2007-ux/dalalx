import type { Candle, Interval, OrderView, Trade } from './types'

/**
 * Pure functions that fold live updates into screen state. Keeping them free
 * of React and of the store makes them trivial to unit-test.
 */

export const ME = 'YOU'

const MINUTE = 60_000
export const INTERVAL_MS: Record<Interval, number> = {
  '1m': MINUTE, '5m': 5 * MINUTE, '15m': 15 * MINUTE, '1D': 24 * 60 * MINUTE,
}

/** Start of the bucket a moment falls into (UTC, like the server's CandleInterval). */
export function bucketStart(millis: number, interval: Interval): number {
  const length = INTERVAL_MS[interval]
  return millis - (millis % length)
}

/** Most candles we keep per chart; matches the server's CandleAggregator cap. */
export const MAX_CANDLES = 500

/**
 * Adds one trade to a candle series the same way the server does: extend the
 * candle for the trade's bucket, or open a new one. Returns a new array
 * (state is immutable; React re-renders only when a new array arrives).
 */
export function addTradeToCandles(candles: Candle[], trade: Trade, interval: Interval): Candle[] {
  const start = bucketStart(Date.parse(trade.timestamp), interval)
  const last = candles[candles.length - 1]
  if (last && last.startMillis === start) {
    const updated: Candle = {
      ...last,
      high: Math.max(last.high, trade.price),
      low: Math.min(last.low, trade.price),
      close: trade.price,
      volume: last.volume + trade.qty,
    }
    return [...candles.slice(0, -1), updated]
  }
  if (last && start < last.startMillis) return candles // a late trade for an old bucket: the server's copy wins on reload
  const opened: Candle = { startMillis: start, open: trade.price, high: trade.price, low: trade.price, close: trade.price, volume: trade.qty }
  return [...candles, opened].slice(-MAX_CANDLES)
}

/** How much of `order` this trade filled (0 if the trade isn't about it). */
export function filledBy(order: OrderView, trade: Trade): number {
  return trade.buyOrderId === order.id || trade.sellOrderId === order.id ? trade.qty : 0
}

/** The order after a fill of `qty`. */
export function applyFill(order: OrderView, qty: number): OrderView {
  const remainingQty = Math.max(0, order.remainingQty - qty)
  return { ...order, remainingQty, status: remainingQty === 0 ? 'FILLED' : 'PARTIALLY_FILLED' }
}

/** Is an order still able to trade (and therefore cancellable)? */
export function isWorking(order: OrderView): boolean {
  return order.status === 'NEW' || order.status === 'PARTIALLY_FILLED'
}

/** Bot keys the design system has portraits for. Trader ids map onto them by name. */
const BOT_KEYS = ['munim', 'momentum', 'panic', 'whale', 'spoofer', 'inspector']

/** "MUNIM" → "munim", "YOU" → "you", anything unknown → undefined (rendered as plain text). */
export function botKey(traderId: string): string | undefined {
  const key = traderId.toLowerCase()
  if (key === 'you') return 'you'
  return BOT_KEYS.includes(key) ? key : undefined
}
