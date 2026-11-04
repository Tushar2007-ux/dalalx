import { describe, expect, it } from 'vitest'
import { changePct, formatPaise, formatQty, parseRupees, toRupees } from './format'

describe('format', () => {
  it('turns paise into rupees only for display', () => {
    expect(toRupees(248650)).toBe(2486.5)
    expect(formatPaise(248650)).toBe('2,486.50')
    expect(formatPaise(12345678)).toBe('1,23,456.78') // Indian grouping
  })

  it('parses typed prices into exact paise without floating point', () => {
    expect(parseRupees('2486.5')).toBe(248650)
    expect(parseRupees('₹2,486.50')).toBe(248650)
    expect(parseRupees('0.29')).toBe(29)
    expect(parseRupees('100')).toBe(10000)
  })

  it('refuses malformed or non-positive prices', () => {
    for (const bad of ['', 'abc', '1.234', '-5', '0', '0.00', '1e3']) expect(parseRupees(bad)).toBeNull()
  })

  it('formats quantities and percentage change', () => {
    expect(formatQty(1234567)).toBe('12,34,567')
    expect(changePct(110, 100)).toBeCloseTo(10)
    expect(changePct(5, 0)).toBe(0)
  })
})
