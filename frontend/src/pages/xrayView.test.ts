import { describe, expect, it } from 'vitest'
import type { XRay } from '../lib/types'
import { mainTraderOf, xrayView } from './xrayView'

const xray: XRay = {
  orderId: 9, symbol: 'RELIANCE', traderId: 'YOU', side: 'BUY', type: 'LIMIT', limitPrice: 248750, qty: 200,
  time: '2026-11-04T09:15:00Z',
  bookBefore: [
    { price: 248675, orders: [{ orderId: 1, traderId: 'MUNIM', remainingQty: 50 }] },
    { price: 248700, orders: [{ orderId: 2, traderId: 'MOMENTUM', remainingQty: 80 }] },
    { price: 248775, orders: [{ orderId: 3, traderId: 'MUNIM', remainingQty: 400 }] },
  ],
  fills: [
    { price: 248675, qty: 50, counterparty: 'MUNIM', restingOrderId: 1, level: 0 },
    { price: 248700, qty: 80, counterparty: 'MOMENTUM', restingOrderId: 2, level: 1 },
  ],
  status: 'PARTIALLY_FILLED', remaining: 70, reason: null,
  steps: [
    { kind: 'ARRIVE', title: '', body: '' }, { kind: 'CHECK', title: '', body: '' },
    { kind: 'FILL', title: '', body: '' }, { kind: 'FILL', title: '', body: '' },
    { kind: 'STOP', title: '', body: '' }, { kind: 'OUTCOME', title: '', body: '' },
  ],
}

describe('xrayView', () => {
  it('shows nothing traded before the first fill step', () => {
    const v = xrayView(xray, 1)
    expect(v).toMatchObject({ fillsShown: 0, remaining: 200, stopShown: false, outcomeShown: false })
  })

  it('takes shares from each level as the fills are replayed', () => {
    const v = xrayView(xray, 3)
    expect(v.fillsShown).toBe(2)
    expect(v.remaining).toBe(70)
    expect(v.takenAt.get(0)).toBe(50)
    expect(v.fillNumbersAt.get(1)).toEqual([1])
  })

  it('reaches the stop and then the outcome', () => {
    expect(xrayView(xray, 4).stopShown).toBe(true)
    expect(xrayView(xray, 5).outcomeShown).toBe(true)
  })

  it('names the trader with the most shares at a level', () => {
    expect(mainTraderOf([{ traderId: 'A', remainingQty: 10 }, { traderId: 'B', remainingQty: 30 }, { traderId: 'A', remainingQty: 25 }])).toBe('A')
    expect(mainTraderOf([])).toBeUndefined()
  })
})
