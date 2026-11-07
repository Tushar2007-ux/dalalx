import { describe, expect, it } from 'vitest'
import { boardTone, sparkPath } from './bigBoard'

describe('Big Board helpers', () => {
  it('glows brighter the bigger the move, capped at full strength', () => {
    expect(boardTone(0)).toEqual({ direction: 'flat', strength: 0.15 })
    expect(boardTone(1.5).direction).toBe('up')
    expect(boardTone(1.5).strength).toBeCloseTo(0.575)
    expect(boardTone(-3)).toEqual({ direction: 'down', strength: 1 })
    expect(boardTone(-9).strength).toBe(1)
  })

  it('draws a sparkline across the box, closing it for the fill', () => {
    expect(sparkPath([1], 100, 50, false)).toBe('')
    const line = sparkPath([10, 20], 100, 50, false)
    expect(line).toBe('M0.0 47.0 L100.0 3.0')
    expect(sparkPath([10, 20], 100, 50, true)).toBe(`${line} L100 50 L0 50 Z`)
  })

  it('draws a flat line when every price is the same', () => {
    expect(sparkPath([5, 5, 5], 100, 50, false)).toBe('M0.0 47.0 L50.0 47.0 L100.0 47.0')
  })
})
