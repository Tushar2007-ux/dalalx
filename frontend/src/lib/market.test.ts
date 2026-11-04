import { describe, expect, it } from 'vitest'
import { addTradeToCandles, applyFill, botKey, bucketStart, isWorking } from './market'
import type { OrderView, Trade } from './types'

const at = (iso: string, price: number, qty = 10): Trade => ({
  id: 1, symbol: 'TCS', price, qty, buyOrderId: 1, sellOrderId: 2, buyerId: 'A', sellerId: 'B',
  aggressorSide: 'BUY', timestamp: iso,
})

describe('candles', () => {
  it('buckets by interval length', () => {
    const t = Date.parse('2026-11-04T09:15:42Z')
    expect(bucketStart(t, '1m')).toBe(Date.parse('2026-11-04T09:15:00Z'))
    expect(bucketStart(t, '15m')).toBe(Date.parse('2026-11-04T09:15:00Z'))
    expect(bucketStart(t, '1D')).toBe(Date.parse('2026-11-04T00:00:00Z'))
  })

  it('opens a candle, then extends it within the same bucket', () => {
    let c = addTradeToCandles([], at('2026-11-04T09:15:01Z', 100), '1m')
    c = addTradeToCandles(c, at('2026-11-04T09:15:20Z', 120, 5), '1m')
    c = addTradeToCandles(c, at('2026-11-04T09:15:59Z', 90), '1m')
    expect(c).toHaveLength(1)
    expect(c[0]).toMatchObject({ open: 100, high: 120, low: 90, close: 90, volume: 25 })
  })

  it('starts a new candle in the next bucket and ignores late trades', () => {
    let c = addTradeToCandles([], at('2026-11-04T09:15:01Z', 100), '1m')
    c = addTradeToCandles(c, at('2026-11-04T09:16:00Z', 101), '1m')
    const before = c
    c = addTradeToCandles(c, at('2026-11-04T09:14:59Z', 50), '1m')
    expect(c).toBe(before)
    expect(c.map((x) => x.open)).toEqual([100, 101])
  })
})

describe('orders', () => {
  const order: OrderView = { id: 7, symbol: 'TCS', traderId: 'YOU', side: 'BUY', type: 'LIMIT', limitPrice: 100, originalQty: 30, remainingQty: 30, status: 'NEW' }

  it('applies partial and full fills', () => {
    const partly = applyFill(order, 10)
    expect(partly).toMatchObject({ remainingQty: 20, status: 'PARTIALLY_FILLED' })
    expect(isWorking(partly)).toBe(true)
    const done = applyFill(partly, 20)
    expect(done).toMatchObject({ remainingQty: 0, status: 'FILLED' })
    expect(isWorking(done)).toBe(false)
  })

  it('maps trader ids onto bot portraits', () => {
    expect(botKey('MUNIM')).toBe('munim')
    expect(botKey('YOU')).toBe('you')
    expect(botKey('stranger')).toBeUndefined()
  })
})
