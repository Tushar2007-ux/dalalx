import { describe, expect, it } from 'vitest'
import type { OrderResult } from '../lib/types'
import { checkTicket, outcomeToast } from './ticket'

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
