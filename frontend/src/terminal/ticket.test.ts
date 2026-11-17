import { describe, expect, it } from 'vitest'
import type { OrderResult } from '../lib/types'
import { checkTicket, outcomeToast, workingLine } from './ticket'

const base = { symbol: 'TCS', side: 'BUY' as const, type: 'LIMIT' as const, priceText: '4090', qtyText: '10', reference: 409000 }

describe('checkTicket', () => {
  it('builds a limit order in paise', () => {
    expect(checkTicket(base).order).toEqual({ symbol: 'TCS', side: 'BUY', type: 'LIMIT', price: 409000, qty: 10 })
  })

  it('ignores the price for a market order', () => {
    expect(checkTicket({ ...base, type: 'MARKET', priceText: 'Market' }).order?.price).toBe(0)
  })

  it.each([
    [{ qtyText: '0' }, 'Quantity'],
    [{ qtyText: '1.5' }, 'Quantity'],
    [{ priceText: 'abc' }, 'Enter a price'],
    [{ priceText: '4090.03' }, 'ticks'],
    [{ priceText: '4600' }, 'circuit band'],
    [{ priceText: '3600' }, 'circuit band'],
  ])('refuses %o', (change, message) => {
    expect(checkTicket({ ...base, ...change }).error).toContain(message)
  })
})

describe('outcomeToast', () => {
  const result = (status: OrderResult['order']['status'], remainingQty: number, reason: string | null = null): OrderResult => ({
    order: { id: 9, symbol: 'TCS', traderId: 'YOU', side: 'BUY', type: 'IOC', limitPrice: 409000, originalQty: 10, remainingQty, status },
    trades: remainingQty < 10 ? [{ id: 1, symbol: 'TCS', price: 408500, qty: 10 - remainingQty, buyOrderId: 9, sellOrderId: 3, buyerId: 'YOU', sellerId: 'MUNIM', aggressorSide: 'BUY', timestamp: '2026-11-04T09:15:00Z' }] : [],
    rested: false,
    reason,
  })

  it('reports each outcome in plain words', () => {
    expect(outcomeToast(result('FILLED', 0))).toMatchObject({ kind: 'filled', title: 'Bought 10 TCS at ₹4,085.00' })
    expect(outcomeToast(result('CANCELLED', 6, 'not immediately fillable'))).toMatchObject({ kind: 'partial', title: 'Bought 4 of 10 TCS' })
    expect(outcomeToast(result('CANCELLED', 10, 'not immediately fillable')).kind).toBe('cancelled')
    expect(outcomeToast(result('REJECTED', 10, 'no liquidity')).detail).toContain('no liquidity')
    expect(outcomeToast(result('NEW', 10)).kind).toBe('info')
  })
})

describe('stop-loss and iceberg tickets', () => {
  const base = { symbol: 'TCS', side: 'SELL' as const, priceText: '4000', qtyText: '500', reference: 400000 }

  it('a stop uses the price as its trigger', () => {
    expect(checkTicket({ ...base, type: 'STOP_LOSS' }).order).toEqual({ symbol: 'TCS', side: 'SELL', type: 'STOP_LOSS', price: 400000, qty: 500 })
  })

  it('an iceberg needs a display size smaller than the order', () => {
    expect(checkTicket({ ...base, type: 'ICEBERG', displayText: '100' }).order?.displayQty).toBe(100)
    expect(checkTicket({ ...base, type: 'ICEBERG', displayText: '500' }).error).toMatch(/Show between/)
    expect(checkTicket({ ...base, type: 'ICEBERG', displayText: '' }).error).toMatch(/Show between/)
  })

  it('describes each working order by what it waits for', () => {
    expect(workingLine({ type: 'STOP_LOSS', side: 'SELL', limitPrice: 395000, remainingQty: 10, originalQty: 10 })).toBe('10/10 · stop ≤ ₹3,950.00')
    expect(workingLine({ type: 'ICEBERG', side: 'BUY', limitPrice: 395000, remainingQty: 900, originalQty: 1000 })).toBe('900/1,000 @ ₹3,950.00 · iceberg')
  })
})
