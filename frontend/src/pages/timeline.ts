import type { Case } from '../lib/types'

/**
 * Geometry for a case's evidence timeline: every order in the 30 seconds
 * before the case opened, as a horizontal line from when it was placed to when
 * it ended, at its price. Pure, so it can be unit-tested.
 */
export const WINDOW_MS = 30_000

export interface Mark {
  id: number
  side: 'bid' | 'ask'
  x0: number   // 0..1 across the window
  x1: number
  price: number
  qty: number
  outcome: 'LIVE' | 'FILLED' | 'CANCELLED'
  flagged: boolean
}

export interface Timeline {
  marks: Mark[]
  low: number
  high: number
}

export function timelineOf(c: Case): Timeline {
  const end = Date.parse(c.openedAt)
  const start = end - WINDOW_MS
  const x = (iso: string | null) => Math.min(1, Math.max(0, ((iso ? Date.parse(iso) : end) - start) / WINDOW_MS))
  const flagged = new Set(c.flagged)
  const marks = c.evidence
    .filter((o) => o.price > 0)
    .map((o) => ({
      id: o.orderId,
      side: o.side === 'BUY' ? 'bid' as const : 'ask' as const,
      x0: x(o.placedAt),
      x1: Math.max(x(o.endedAt), x(o.placedAt) + 0.004),
      price: o.price,
      qty: o.qty,
      outcome: o.outcome,
      flagged: flagged.has(o.orderId),
    }))
  const prices = marks.map((m) => m.price)
  return { marks, low: prices.length ? Math.min(...prices) : 0, high: prices.length ? Math.max(...prices) : 1 }
}
