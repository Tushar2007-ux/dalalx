import { formatPaise, formatQty, parseRupees } from '../lib/format'
import type { OrderResult, OrderType, PlaceOrder, Side } from '../lib/types'
import type { ToastItem } from '../store/market'

/**
 * Order-ticket rules, kept out of the React component so they can be tested
 * on their own: what the trader typed → either a valid order or one clear error.
 */

/** Price band: limit prices further than this from the last traded price are refused (matches the 10% circuit breaker). */
export const BAND = 0.10

export interface TicketInput {
  symbol: string
  side: Side
  type: OrderType
  priceText: string
  qtyText: string
  /** ICEBERG only: how many shares to show at a time. */
  displayText?: string
  reference?: number
}

export type TicketCheck = { order: PlaceOrder; error?: undefined } | { order?: undefined; error: string }

export function bandOf(reference: number): [number, number] {
  return [Math.ceil(reference * (1 - BAND)), Math.floor(reference * (1 + BAND))]
}

export function checkTicket(t: TicketInput): TicketCheck {
  const qty = /^\d+$/.test(t.qtyText.trim()) ? Number(t.qtyText.trim()) : NaN
  if (!Number.isSafeInteger(qty) || qty <= 0) return { error: 'Quantity must be a whole number above zero' }
  if (t.type === 'MARKET') return { order: { symbol: t.symbol, side: t.side, type: 'MARKET', price: 0, qty } }

  const price = parseRupees(t.priceText)
  if (price === null) return { error: 'Enter a price like 2486.50' }
  if (price % 5 !== 0) return { error: 'Prices move in ticks of ₹0.05' }
  if (t.reference) {
    const [low, high] = bandOf(t.reference)
    if (price < low || price > high) return { error: "Outside today's circuit band" }
  }
  if (t.type === 'ICEBERG') {
    const text = (t.displayText ?? '').trim()
    const shown = /^\d+$/.test(text) ? Number(text) : NaN
    if (!(shown >= 1 && shown < qty)) return { error: `Show between 1 and ${Math.max(1, qty - 1)} shares at a time` }
    return { order: { symbol: t.symbol, side: t.side, type: 'ICEBERG', price, qty, displayQty: shown } }
  }
  return { order: { symbol: t.symbol, side: t.side, type: t.type, price, qty } }
}

/** The ticket's choice of order types, with a line on what each does. */
export const ORDER_TYPES: { value: string; label: string; hint: string }[] = [
  { value: 'limit', label: 'Limit', hint: 'Rests in the book at your price.' },
  { value: 'market', label: 'Market', hint: 'Fills now at the best available price.' },
  { value: 'ioc', label: 'IOC', hint: 'Immediate or cancel: fills what it can, cancels the rest.' },
  { value: 'stop_loss', label: 'Stop', hint: 'Waits unseen; when the price reaches your trigger it trades at the market.' },
  { value: 'iceberg', label: 'Iceberg', hint: 'A limit order that shows only part of its size at a time.' },
]

/** One line for a working order in "Your Orders": what it is waiting for. */
export function workingLine(o: { type: OrderType; side: Side; limitPrice: number; remainingQty: number; originalQty: number }): string {
  const size = `${formatQty(o.remainingQty)}/${formatQty(o.originalQty)}`
  if (o.type === 'STOP_LOSS') return `${size} · stop ${o.side === 'SELL' ? '≤' : '≥'} ₹${formatPaise(o.limitPrice)}`
  if (o.type === 'ICEBERG') return `${size} @ ₹${formatPaise(o.limitPrice)} · iceberg`
  return `${size} @ ₹${formatPaise(o.limitPrice)}`
}

/** Volume-weighted average price of the fills, in paise. */
function averagePrice(result: OrderResult): number {
  const qty = result.trades.reduce((sum, t) => sum + t.qty, 0)
  const value = result.trades.reduce((sum, t) => sum + t.price * t.qty, 0)
  return qty ? Math.round(value / qty) : 0
}

/** The toast that reports what the exchange did with an order. */
export function outcomeToast(result: OrderResult): Omit<ToastItem, 'id'> {
  const o = result.order
  const filled = o.originalQty - o.remainingQty
  const verb = o.side === 'BUY' ? 'Bought' : 'Sold'
  const ref = `#${o.id} · ${o.type}`
  switch (o.status) {
    case 'REJECTED':
      return { kind: 'rejected', title: `${o.symbol} order rejected`, detail: `${ref} · ${result.reason ?? 'refused'}` }
    case 'FILLED':
      return { kind: 'filled', title: `${verb} ${formatQty(filled)} ${o.symbol} at ₹${formatPaise(averagePrice(result))}`, detail: ref }
    case 'PARTIALLY_FILLED':
      return { kind: 'partial', title: `${verb} ${formatQty(filled)} of ${formatQty(o.originalQty)} ${o.symbol}`,
        detail: `${ref} · ${formatQty(o.remainingQty)} resting at ₹${formatPaise(o.limitPrice)}` }
    case 'CANCELLED':
      return filled
        ? { kind: 'partial', title: `${verb} ${formatQty(filled)} of ${formatQty(o.originalQty)} ${o.symbol}`, detail: `${ref} · rest cancelled: ${result.reason}` }
        : { kind: 'cancelled', title: `${o.symbol} order cancelled`, detail: `${ref} · ${result.reason}` }
    default:
      return { kind: 'info', title: `Order resting: ${formatQty(o.originalQty)} ${o.symbol} at ₹${formatPaise(o.limitPrice)}`, detail: ref }
  }
}
