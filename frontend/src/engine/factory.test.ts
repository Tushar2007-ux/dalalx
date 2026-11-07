import { describe, expect, it } from 'vitest'
import { arrivalRate, compact, queueFill, threadMood, ticketScale, ticketsFor } from './factory'

describe('Engine Room helpers', () => {
  it('draws one ticket per command until a lane gets busy', () => {
    expect(ticketScale(5)).toBe(1)
    expect(ticketScale(36)).toBe(1)
    expect(ticketScale(37)).toBe(2)
    expect(ticketScale(40_000)).toBe(1112)
  })

  it('carries fractions so slow sources still print at the right average rate', () => {
    const carry = {}
    let printed = 0
    for (let i = 0; i < 20; i++) printed += ticketsFor({ BOT: 2.5 }, 0.1, carry, 1).length
    expect(printed).toBe(5) // 2.5 a second for 2 seconds
  })

  it('prints tickets from every source in proportion', () => {
    const tickets = ticketsFor({ CLOCK: 50, BOT: 20 }, 0.1, {}, 1)
    expect(tickets.filter((t) => t === 'CLOCK')).toHaveLength(5)
    expect(tickets.filter((t) => t === 'BOT')).toHaveLength(2)
  })

  it('sums arrivals over sources', () => {
    expect(arrivalRate({ arrivalsPerSecond: { CLOCK: 5, BOT: 2.5 } } as never)).toBe(7.5)
  })

  it('fills the queue bin on a log scale', () => {
    expect(queueFill(0, 100_000)).toBe(0)
    expect(queueFill(9, 100_000)).toBeCloseTo(0.2, 1)
    expect(queueFill(100_000, 100_000)).toBe(1)
    expect(queueFill(500_000, 100_000)).toBe(1)
  })

  it('describes the thread in plain English', () => {
    expect(threadMood({ state: 'WAITING', running: null, pending: 0, perSecond: 5 })).toBe('Asleep in take()')
    expect(threadMood({ state: 'RUNNABLE', running: 'submit', pending: 0, perSecond: 5 })).toBe('Pressing')
    expect(threadMood({ state: 'WAITING', running: null, pending: 3, perSecond: 5 })).toBe('Pressing')
    expect(threadMood({ state: 'TERMINATED', running: null, pending: 0, perSecond: 0 })).toBe('Stopped')
  })

  it('prints big counts compactly', () => {
    expect(compact(950)).toBe('950')
    expect(compact(12_400)).toBe('12k')
    expect(compact(4_200)).toBe('4.2k')
    expect(compact(2_300_000)).toBe('2.3M')
  })
})
