import { describe, expect, it } from 'vitest'
import { pushReading } from './Sparkline'

describe('pushReading', () => {
  it('keeps only the most recent readings', () => {
    let h: number[] = []
    for (let i = 0; i < 70; i++) h = pushReading(h, i)
    expect(h).toHaveLength(60)
    expect(h[0]).toBe(10)
    expect(h[59]).toBe(69)
  })
})
