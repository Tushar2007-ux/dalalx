import { describe, expect, it } from 'vitest'
import { binFill, laneX, shareBudget, slipRate, strokesPerSecond, trayHeight } from './hall'

describe('laneX', () => {
  it('centres the hall on zero', () => {
    const xs = [0, 1, 2, 3, 4].map((i) => laneX(i, 5))
    expect(xs[2]).toBe(0)
    expect(xs[0]).toBe(-xs[4])
  })
})

describe('strokesPerSecond', () => {
  it('lets an idle press rest', () => {
    expect(strokesPerSecond(0)).toBe(0)
  })

  it('goes faster with more work, but never into a blur', () => {
    expect(strokesPerSecond(1000)).toBeGreaterThan(strokesPerSecond(10))
    expect(strokesPerSecond(1_000_000)).toBeLessThanOrEqual(6)
  })
})

describe('binFill', () => {
  it('is empty when nothing is waiting, and full at capacity', () => {
    expect(binFill(0, 100_000)).toBe(0)
    expect(binFill(100_000, 100_000)).toBe(1)
  })

  it('shows a small queue rather than hiding it', () => {
    // 200 of 100,000 is 0.2% — a straight bar would be invisible.
    expect(binFill(200, 100_000)).toBeGreaterThan(0.1)
  })

  it('never exceeds full, even past capacity', () => {
    expect(binFill(500_000, 100_000)).toBe(1)
  })
})

describe('slipRate', () => {
  it('sends no paper down an idle lane', () => {
    expect(slipRate({ perSecond: 0, pending: 0 })).toBe(0)
  })

  it('rises with traffic and stops at a sane ceiling', () => {
    expect(slipRate({ perSecond: 50, pending: 0 })).toBeGreaterThan(slipRate({ perSecond: 5, pending: 0 }))
    expect(slipRate({ perSecond: 500_000, pending: 90_000 })).toBeLessThanOrEqual(14)
  })
})

describe('trayHeight', () => {
  it('starts flat and grows without running off the top', () => {
    expect(trayHeight(0)).toBe(0)
    expect(trayHeight(10 ** 12)).toBeLessThanOrEqual(0.9)
  })
})

describe('shareBudget', () => {
  it('spends nothing when nothing is moving', () => {
    expect(shareBudget([0, 0, 0], 90)).toEqual([0, 0, 0])
  })

  it('gives the busiest lane the most paper, within the budget', () => {
    const share = shareBudget([10, 5, 1], 80)
    expect(share[0]).toBeGreaterThan(share[1])
    expect(share.reduce((a, b) => a + b, 0)).toBeLessThanOrEqual(81)
  })
})
