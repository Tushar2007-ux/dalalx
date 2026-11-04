import { beforeEach, describe, expect, it } from 'vitest'
import type { OrderView, Trade } from '../lib/types'
import { useMarket } from './market'

const mine: OrderView = { id: 5, symbol: 'TCS', traderId: 'YOU', side: 'SELL', type: 'LIMIT', limitPrice: 409000, originalQty: 30, remainingQty: 30, status: 'NEW' }
const trade = (qty: number, aggressorSide: 'BUY' | 'SELL', id = 1): Trade => ({
  id, symbol: 'TCS', price: 409000, qty, buyOrderId: 99, sellOrderId: 5, buyerId: 'MUNIM', sellerId: 'YOU',
  aggressorSide, timestamp: '2026-11-04T09:15:00Z',
})

describe('market store', () => {
  beforeEach(() => useMarket.setState({ myOrders: {}, toasts: [], tapes: {}, lastPrice: {}, chart: null, interval: '1m' }))

  it('fills my resting order when someone hits it, and tells me', () => {
    useMarket.getState().trackOrder(mine)
    useMarket.getState().applyTrades('TCS', [trade(10, 'BUY')])
    const s = useMarket.getState()
    expect(s.myOrders[5]).toMatchObject({ remainingQty: 20, status: 'PARTIALLY_FILLED' })
    expect(s.toasts[0].title).toBe('Sold 10 TCS at ₹4,090.00')
    expect(s.lastPrice.TCS).toBe(409000)
    expect(s.tapes.TCS).toHaveLength(1)
  })

  it('does not count my own aggressive fills twice (the REST reply already did)', () => {
    useMarket.getState().trackOrder({ ...mine, remainingQty: 20, status: 'PARTIALLY_FILLED' })
    useMarket.getState().applyTrades('TCS', [trade(10, 'SELL')])
    expect(useMarket.getState().myOrders[5].remainingQty).toBe(20)
    expect(useMarket.getState().toasts).toHaveLength(0)
  })

  it('keeps the newest trades first and merges them into the visible chart', () => {
    useMarket.getState().setCandles('TCS', '1m', [])
    useMarket.getState().applyTrades('TCS', [trade(1, 'BUY', 1), trade(2, 'BUY', 2)])
    const s = useMarket.getState()
    expect(s.tapes.TCS.map((t) => t.id)).toEqual([2, 1])
    expect(s.chart?.candles[0].volume).toBe(3)
  })

  it('ignores trades it has already seen (REST snapshot and stream overlap)', () => {
    useMarket.getState().setTape('TCS', [trade(5, 'BUY', 7)])
    useMarket.getState().applyTrades('TCS', [trade(5, 'BUY', 7), trade(3, 'BUY', 8)])
    expect(useMarket.getState().tapes.TCS.map((t) => t.id)).toEqual([8, 7])
    useMarket.getState().setTape('TCS', [trade(5, 'BUY', 7)])
    expect(useMarket.getState().tapes.TCS.map((t) => t.id)).toEqual([8, 7])
  })

  it('marks my order cancelled when the exchange says so', () => {
    useMarket.getState().trackOrder(mine)
    useMarket.getState().applyActivity([{ kind: 'CANCELLED', symbol: 'TCS', orderId: 5, traderId: 'YOU', side: 'SELL', type: 'LIMIT', price: 409000, qty: 30, remainingQty: 30, reason: 'cancelled by trader' }])
    expect(useMarket.getState().myOrders[5].status).toBe('CANCELLED')
  })

  it('keeps at most three toasts', () => {
    for (let i = 0; i < 5; i++) useMarket.getState().toast({ kind: 'info', title: `t${i}` })
    expect(useMarket.getState().toasts.map((t) => t.title)).toEqual(['t4', 't3', 't2'])
  })
})

describe('the wire', () => {
  const h = (id: number) => ({ id, time: '2026-11-04T09:15:00Z', kicker: 'Markets', symbol: 'TCS', text: `story ${id}`, deck: null, tone: 'UP' as const })

  it('merges a late REST snapshot with pushed headlines, newest first, without duplicates', () => {
    useMarket.setState({ wire: [] })
    useMarket.getState().applyHeadline(h(5))
    useMarket.getState().applyHeadline(h(5))
    useMarket.getState().setWire([h(4), h(3)])
    expect(useMarket.getState().wire.map((x) => x.id)).toEqual([5, 4, 3])
  })
})

describe('market status', () => {
  it('records a halt with a toast, and announces the reopening', () => {
    useMarket.setState({ status: { TCS: { phase: 'OPEN', reason: null, until: null } }, toasts: [] })
    useMarket.getState().applyStatus({ symbol: 'TCS', timestamp: '2026-11-04T09:15:00Z', phase: 'HALTED', reason: 'TCS fell 10.2% in 60 seconds', until: '2026-11-04T09:15:30Z' })
    expect(useMarket.getState().status.TCS.phase).toBe('HALTED')
    expect(useMarket.getState().toasts[0].title).toBe('Trading halted in TCS')

    useMarket.getState().applyStatus({ symbol: 'TCS', timestamp: '2026-11-04T09:15:30Z', phase: 'OPEN', reason: null, until: null })
    expect(useMarket.getState().status.TCS).toEqual({ phase: 'OPEN', reason: null, until: null, since: '2026-11-04T09:15:30Z' })
    expect(useMarket.getState().toasts[0].title).toBe('Trading resumes in TCS')
  })
})

describe('surveillance cases', () => {
  const c = (id: string, status: 'OPEN' | 'SUSPENDED') => ({
    id, ruleId: 'spoofing', ruleName: 'Spoofing', severity: 'CRITICAL' as const, traderId: 'SPOOFER', symbol: 'RELIANCE',
    openedAt: '2026-11-04T09:15:00Z', summary: '3 large orders', figures: {}, notes: [], evidence: [], flagged: [], status,
  })

  it('adds new cases with a toast and updates known ones in place', () => {
    useMarket.setState({ cases: [], toasts: [] })
    useMarket.getState().applyCase(c('SV-0001', 'OPEN'))
    expect(useMarket.getState().toasts[0].title).toBe('Spoofing · RELIANCE: case SV-0001 opened')
    useMarket.getState().applyCase(c('SV-0001', 'SUSPENDED'))
    expect(useMarket.getState().cases).toHaveLength(1)
    expect(useMarket.getState().cases[0].status).toBe('SUSPENDED')
    expect(useMarket.getState().toasts).toHaveLength(1)
  })
})
