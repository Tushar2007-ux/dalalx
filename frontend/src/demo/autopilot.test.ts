import { beforeEach, describe, expect, it } from 'vitest'
import type { MarketState } from '../store/market'
import { stepDone, useDemo } from './autopilot'
import { DEMO, DEMO_MIN_SECONDS } from './script'

const market = (phase: 'OPEN' | 'HALTED' | 'AUCTION') => ({ status: { RELIANCE: { phase, reason: null, until: null } } }) as unknown as MarketState

describe('demo autopilot', () => {
  beforeEach(() => useDemo.getState().stop())

  it('moves on after a plain step’s time is up', () => {
    const step = DEMO[0]
    expect(stepDone(step, step.seconds - 0.25, market('OPEN'))).toBe(false)
    expect(stepDone(step, step.seconds, market('OPEN'))).toBe(true)
  })

  it('waits for the market on a waiting step, but never longer than its maximum', () => {
    const crash = DEMO.find((s) => s.title === 'Flash crash')!
    expect(stepDone(crash, 20, market('OPEN'))).toBe(false)
    expect(stepDone(crash, 20, market('HALTED'))).toBe(true)
    expect(stepDone(crash, crash.maxSeconds!, market('OPEN'))).toBe(true)
  })

  it('walks through every step and then stops', () => {
    useDemo.getState().start()
    for (let i = 0; i < DEMO.length; i++) useDemo.getState().next()
    expect(useDemo.getState().running).toBe(false)
  })

  it('is a few minutes long', () => {
    expect(DEMO_MIN_SECONDS).toBeGreaterThan(90)
    expect(DEMO.map((s) => s.route)).toContain('surveillance')
  })
})
