import { describe, expect, it } from 'vitest'
import { type Machine, pixelRatioFor, slipBudget, tierFor } from './quality'

const machine = (over: Partial<Machine> = {}): Machine => ({
  webgl: true, reducedMotion: false, memoryGb: 8, cores: 8, pixelRatio: 2, width: 1440, ...over,
})

describe('tierFor', () => {
  it('gives a capable desktop the full version', () => {
    expect(tierFor(machine())).toBe('high')
  })

  it('draws nothing without WebGL, or when less motion was asked for', () => {
    expect(tierFor(machine({ webgl: false }))).toBe('off')
    expect(tierFor(machine({ reducedMotion: true }))).toBe('off')
  })

  it('drops to the cheap version on a phone, a weak CPU or little memory', () => {
    expect(tierFor(machine({ width: 390 }))).toBe('low')
    expect(tierFor(machine({ cores: 2 }))).toBe('low')
    expect(tierFor(machine({ memoryGb: 2 }))).toBe('low')
  })

  it('assumes a middling machine when the browser will not say', () => {
    expect(tierFor(machine({ cores: undefined, memoryGb: undefined }))).toBe('high')
  })
})

describe('pixelRatioFor', () => {
  it('never draws more pixels than the screen has', () => {
    expect(pixelRatioFor('high', 1)).toBe(1)
  })

  it('caps a dense screen, harder on the cheap tier', () => {
    expect(pixelRatioFor('high', 3)).toBe(2)
    expect(pixelRatioFor('low', 3)).toBe(1.25)
  })

  it('draws nothing at all when it is off', () => {
    expect(pixelRatioFor('off', 2)).toBe(0)
  })
})

describe('slipBudget', () => {
  it('spends paper in proportion to the machine', () => {
    expect(slipBudget('high')).toBeGreaterThan(slipBudget('low'))
    expect(slipBudget('off')).toBe(0)
  })
})
