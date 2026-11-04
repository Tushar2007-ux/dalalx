import { describe, expect, it } from 'vitest'
import type { Case } from '../lib/types'
import { timelineOf } from './timeline'

const base: Case = {
  id: 'SV-0001', ruleId: 'spoofing', ruleName: 'Spoofing', severity: 'CRITICAL', traderId: 'SPOOFER', symbol: 'RELIANCE',
  openedAt: '2026-11-04T09:15:30Z', summary: '', figures: {}, notes: ['a', 'b'], flagged: [1], status: 'OPEN',
  evidence: [
    { orderId: 1, traderId: 'SPOOFER', symbol: 'RELIANCE', side: 'BUY', price: 245000, qty: 9800, placedAt: '2026-11-04T09:15:15Z', endedAt: '2026-11-04T09:15:15.300Z', outcome: 'CANCELLED', filled: 0 },
    { orderId: 2, traderId: 'SPOOFER', symbol: 'RELIANCE', side: 'SELL', price: 245500, qty: 50, placedAt: '2026-11-04T09:15:29Z', endedAt: null, outcome: 'LIVE', filled: 0 },
    { orderId: 3, traderId: 'SPOOFER', symbol: 'RELIANCE', side: 'SELL', price: 0, qty: 50, placedAt: '2026-11-04T09:15:29Z', endedAt: null, outcome: 'FILLED', filled: 50 },
  ],
}

describe('timelineOf', () => {
  it('places each order across the 30 seconds before the case', () => {
    const t = timelineOf(base)
    expect(t.marks).toHaveLength(2) // the market order has no price to draw
    expect(t.marks[0]).toMatchObject({ side: 'bid', x0: 0.5, flagged: true, outcome: 'CANCELLED' })
    expect(t.marks[0].x1).toBeCloseTo(0.51)
    expect(t.marks[1]).toMatchObject({ side: 'ask', x1: 1, flagged: false }) // still live: runs to the end
    expect([t.low, t.high]).toEqual([245000, 245500])
  })
})
