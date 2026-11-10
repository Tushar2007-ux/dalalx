import { describe, expect, it } from 'vitest'
import { effectFor, play } from './sound'

describe('sound effects', () => {
  it('match each big moment', () => {
    expect(effectFor('bell')).toBe('bell')
    expect(effectFor('extra')).toBe('thud')
    expect(effectFor('caught')).toBe('stamp')
  })

  it('stay silent (and never throw) where there is no audio', () => {
    expect(() => play('bell')).not.toThrow()
  })
})
