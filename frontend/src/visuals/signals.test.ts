import { describe, expect, it } from 'vitest'
import type { WireHeadline } from '../lib/types'
import {
  BREAKING_MS, botMoodOf, breakingNow, depthCurve, fillMarks, layoutTiles, levelHeat, moveInk,
  pushFill, pushPrice, swing, trailPath, weatherFor,
} from './signals'

describe('pushPrice', () => {
  it('starts a trail and appends to it', () => {
    expect(pushPrice(undefined, 100)).toEqual([100])
    expect(pushPrice([100], 101)).toEqual([100, 101])
  })

  it('drops the oldest price once full', () => {
    expect(pushPrice([1, 2, 3], 4, 3)).toEqual([2, 3, 4])
  })
})

describe('trailPath', () => {
  it('draws nothing from a single point', () => {
    expect(trailPath([100], 40, 12)).toBe('')
  })

  it('runs left to right, high prices at the top', () => {
    const path = trailPath([100, 110], 40, 12, 0)
    expect(path).toBe('M0.0 12.0 L40.0 0.0')
  })

  it('draws a flat line for a flat price rather than dividing by zero', () => {
    expect(trailPath([100, 100, 100], 40, 12)).not.toContain('NaN')
  })
})

describe('depthCurve', () => {
  it('accumulates size walking away from the best price', () => {
    const curve = depthCurve([{ price: 100, totalQty: 50 }, { price: 99, totalQty: 30 }, { price: 98, totalQty: 20 }])
    expect(curve.map((p) => p.total)).toEqual([50, 80, 100])
    expect(curve[2].price).toBe(98)
  })

  it('is empty for an empty side', () => {
    expect(depthCurve([])).toEqual([])
  })
})

describe('layoutTiles', () => {
  const items = [{ key: 'A', weight: 50 }, { key: 'B', weight: 30 }, { key: 'C', weight: 20 }]

  it('covers the whole rectangle with no overlap', () => {
    const tiles = layoutTiles(items, 300, 200)
    const area = tiles.reduce((sum, t) => sum + t.w * t.h, 0)
    expect(area).toBeCloseTo(300 * 200, 3)
  })

  it('gives each tile area in proportion to its weight', () => {
    const tiles = layoutTiles(items, 300, 200)
    const a = tiles.find((t) => t.key === 'A')!
    expect((a.w * a.h) / (300 * 200)).toBeCloseTo(0.5, 3)
  })

  it('leaves out anything with no weight', () => {
    expect(layoutTiles([{ key: 'A', weight: 1 }, { key: 'Z', weight: 0 }], 10, 10)).toHaveLength(1)
  })
})

describe('moveInk', () => {
  it('grows with the move and stops at full strength', () => {
    expect(moveInk(0)).toBe(0)
    expect(moveInk(-1.5)).toBeCloseTo(0.5)
    expect(moveInk(12)).toBe(1)
  })
})

describe('market weather', () => {
  it('reads a flat tape as no swing at all', () => {
    expect(swing([[100, 100, 100]])).toBe(0)
    expect(swing([])).toBe(0)
  })

  it('measures swing as range over mean, averaged across stocks', () => {
    // 98..102 around 100 is a 4% range; the flat stock contributes 0.
    expect(swing([[98, 100, 102], [50, 50]])).toBeCloseTo(2, 5)
  })

  it('goes from fair to stormy as swings widen', () => {
    expect(weatherFor(0.1, false)).toBe('fair')
    expect(weatherFor(0.8, false)).toBe('breezy')
    expect(weatherFor(2, false)).toBe('unsettled')
    expect(weatherFor(5, false)).toBe('stormy')
  })

  it('calls any halt a storm, however calm the rest', () => {
    expect(weatherFor(0, true)).toBe('stormy')
  })
})

describe('levelHeat', () => {
  it('is cold for nothing and hottest for the biggest level', () => {
    expect(levelHeat(0, 500)).toBe(0)
    expect(levelHeat(500, 500)).toBe(1)
  })

  it('keeps mid-sized levels visibly warm', () => {
    // A quarter of the biggest level still shows at half strength.
    expect(levelHeat(125, 500)).toBeCloseTo(0.5)
  })
})

describe('breakingNow', () => {
  const at = Date.parse('2026-01-01T10:00:00Z')
  const headline = (tone: WireHeadline['tone'], time: string): WireHeadline =>
    ({ id: 1, time, kicker: 'K', symbol: 'TCS', text: 'T', deck: null, tone })

  it('holds the band for a fresh alert', () => {
    expect(breakingNow([headline('ALERT', '2026-01-01T09:59:58Z')], at)?.id).toBe(1)
  })

  it('lets an alert go once it is old news', () => {
    const old = new Date(at - BREAKING_MS - 1).toISOString()
    expect(breakingNow([headline('ALERT', old)], at)).toBeNull()
  })

  it('never interrupts for ordinary news', () => {
    expect(breakingNow([headline('UP', '2026-01-01T09:59:59Z')], at)).toBeNull()
    expect(breakingNow([], at)).toBeNull()
  })
})

describe('botMoodOf', () => {
  const calm = { suspended: false, underInvestigation: false, movePct: 0, tradedJustNow: false }

  it('puts a suspension above everything else', () => {
    expect(botMoodOf('SPOOFER', { ...calm, suspended: true, underInvestigation: true })).toBe('suspended')
  })

  it('shows a bot under investigation as watched', () => {
    expect(botMoodOf('SPOOFER', { ...calm, underInvestigation: true })).toBe('watched')
  })

  it('makes the Panic Seller sweat only in a fall', () => {
    expect(botMoodOf('PANIC', { ...calm, movePct: -1.2 })).toBe('sweating')
    expect(botMoodOf('PANIC', { ...calm, movePct: 1.2 })).toBe('calm')
  })

  it('sets Momentum chasing a big move in either direction', () => {
    expect(botMoodOf('MOMENTUM', { ...calm, movePct: 1 })).toBe('chasing')
    expect(botMoodOf('MOMENTUM', { ...calm, movePct: -1 })).toBe('chasing')
  })

  it('is busy just after a trade and calm otherwise', () => {
    expect(botMoodOf('MUNIM', { ...calm, tradedJustNow: true })).toBe('busy')
    expect(botMoodOf('MUNIM', calm)).toBe('calm')
  })
})

describe('my fills on the chart', () => {
  const fill = (at: number) => ({ at, price: 100, side: 'BUY' as const, qty: 5 })

  it('puts a fill on the candle that was open at the time', () => {
    const marks = fillMarks([1000, 2000, 3000], [fill(2500)])
    expect(marks).toEqual([{ index: 1, price: 100, side: 'BUY', qty: 5 }])
  })

  it('puts a fill after the last candle opened on that last candle', () => {
    expect(fillMarks([1000, 2000], [fill(9999)])[0].index).toBe(1)
  })

  it('merges the several fills of one order on a candle into a single mark at the average price', () => {
    const fills = [{ at: 1100, price: 100, side: 'BUY' as const, qty: 10 }, { at: 1200, price: 104, side: 'BUY' as const, qty: 30 }]
    expect(fillMarks([1000], fills)).toEqual([{ index: 0, price: 103, side: 'BUY', qty: 40 }])
  })

  it('keeps a buy and a sell on the same candle apart', () => {
    const fills = [fill(1100), { at: 1200, price: 101, side: 'SELL' as const, qty: 5 }]
    expect(fillMarks([1000], fills)).toHaveLength(2)
  })

  it('leaves off a fill older than every candle on screen', () => {
    expect(fillMarks([1000, 2000], [fill(500)])).toEqual([])
  })

  it('forgets the oldest fill once the memory is full', () => {
    expect(pushFill([fill(1), fill(2)], fill(3), 2).map((f) => f.at)).toEqual([2, 3])
  })
})
