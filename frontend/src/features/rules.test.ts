import { describe, expect, it } from 'vitest'
import type { DayStock, RewindView } from '../lib/types'
import {
  checkCredentials, checkListing, checkRecipe, clockOf, dayMove, describeRecipe, leadHeadline, moversOf,
  progressOf, rewindMove, secondsLeft, sessionLabel, type RecipeForm,
} from './rules'

describe('account credentials', () => {
  it('accepts a sensible name and PIN', () => {
    expect(checkCredentials('Priya S', '4321')).toBeNull()
  })
  it('refuses short names, odd characters and bad PINs', () => {
    expect(checkCredentials('P', '4321')).toMatch(/name/)
    expect(checkCredentials("Bob'; --", '4321')).toMatch(/name/)
    expect(checkCredentials('Priya', '12')).toMatch(/PIN/)
    expect(checkCredentials('Priya', 'abcd')).toMatch(/PIN/)
  })
})

describe('the bot builder', () => {
  const form: RecipeForm = { name: 'Dip Buyer', symbol: 'TCS', when: 'FALLS', amountText: '1.5', side: 'BUY', qtyText: '200', maxText: '1000' }

  it('turns a percent into basis points', () => {
    expect(checkRecipe(form).recipe).toEqual({ name: 'Dip Buyer', symbol: 'TCS', when: 'FALLS', amount: 150, side: 'BUY', qty: 200, maxShares: 1000 })
  })
  it('reads EVERY as seconds', () => {
    expect(checkRecipe({ ...form, when: 'EVERY', amountText: '10' }).recipe?.amount).toBe(10)
  })
  it('gives one clear error at a time', () => {
    expect(checkRecipe({ ...form, name: '!!' }).error).toMatch(/name/)
    expect(checkRecipe({ ...form, amountText: '0.01' }).error).toMatch(/0.1%/)
    expect(checkRecipe({ ...form, when: 'EVERY', amountText: '0' }).error).toMatch(/seconds/)
    expect(checkRecipe({ ...form, qtyText: '0' }).error).toMatch(/shares/)
    expect(checkRecipe({ ...form, maxText: '100' }).error).toMatch(/holding limit/)
  })
  it('reads the rule back as a sentence, matching the server', () => {
    expect(describeRecipe(checkRecipe(form).recipe!)).toBe('Buys 200 TCS whenever the price falls 1.50%, holding at most 1000.')
  })
})

describe('listing a stock', () => {
  it('upper-cases the symbol and reads the price in rupees', () => {
    expect(checkListing('wipro', 'Wipro', '250', ['TCS'])).toEqual({ symbol: 'WIPRO', name: 'Wipro', price: 25000 })
  })
  it('refuses a duplicate, a bad symbol, a full exchange and an off-tick price', () => {
    expect(checkListing('TCS', '', '100', ['TCS'])).toHaveProperty('error')
    expect(checkListing('9X', '', '100', [])).toHaveProperty('error')
    expect(checkListing('NEW', '', '100', Array.from({ length: 12 }, (_, i) => `S${i}`))).toHaveProperty('error')
    expect(checkListing('NEW', '', '100.03', [])).toHaveProperty('error')
  })
})

describe('the tournament clock', () => {
  it('counts down to the end and stops at zero', () => {
    const t = { state: 'RUNNING' as const, startedAt: null, endsAt: '2026-11-17T10:01:00Z', scores: [] }
    expect(secondsLeft(t, Date.parse('2026-11-17T10:00:00Z'))).toBe(60)
    expect(secondsLeft(t, Date.parse('2026-11-17T10:02:00Z'))).toBe(0)
    expect(secondsLeft({ ...t, state: 'IDLE' }, 0)).toBe(0)
    expect(clockOf(65)).toBe('1:05')
  })
})

describe('the rewind', () => {
  it('measures progress and moves', () => {
    expect(progressOf({ done: 30, total: 120 } as RewindView)).toBe(25)
    expect(progressOf(null)).toBe(0)
    expect(rewindMove(10000, 10500)).toBeCloseTo(5)
    expect(rewindMove(0, 10)).toBe(0)
  })
  it('names a session by its start time', () => {
    expect(sessionLabel('session-20261117-091500.log')).toBe('17 Nov 2026, 09:15 UTC')
    expect(sessionLabel('notes.log')).toBe('notes.log')
  })
})

describe('the evening edition', () => {
  const stock = (symbol: string, last: number, turnover = 1, trades = 1): DayStock =>
    ({ symbol, name: symbol, reference: 10000, open: 10000, high: last, low: last, last, volume: 1, turnover, trades })

  it('finds the gainers, losers and the busiest stock', () => {
    const day = [stock('A', 10500, 5), stock('B', 9500, 50), stock('C', 10100, 1), stock('D', 10000, 0, 0)]
    const m = moversOf(day)
    expect(m.gainers.map((s) => s.symbol)).toEqual(['A', 'C'])
    expect(m.losers.map((s) => s.symbol)).toEqual(['B'])
    expect(m.busiest?.symbol).toBe('B')
    expect(dayMove(day[3])).toBe(0)
  })
  it('writes a lead headline from the figures', () => {
    expect(leadHeadline([stock('A', 10500), stock('B', 9900)], 0)).toBe('A leads the market, up 5.0%')
    expect(leadHeadline([stock('B', 9000)], 1)).toBe('Breakers trip as B slides 10.0%')
    expect(leadHeadline([], 0)).toBe('A quiet day on the floor')
  })
})
