import { describe, expect, it } from 'vitest'
import { effectFor, effectForHeadline, play, TYPE_GAP_MS, typewriterDue } from './sound'

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

describe('the newer sounds', () => {
  it('chime for company news and ring the bell for a tournament result', () => {
    expect(effectForHeadline('Simulated news')).toBe('chime')
    expect(effectForHeadline('Tournament')).toBe('bell')
    expect(effectForHeadline('Markets')).toBeNull()
  })

  it('rate-limit the typewriter', () => {
    expect(typewriterDue(1000, 1000 + TYPE_GAP_MS - 1)).toBe(false)
    expect(typewriterDue(1000, 1000 + TYPE_GAP_MS)).toBe(true)
  })

  it('never throw where there is no audio', () => {
    expect(() => play('type')).not.toThrow()
    expect(() => play('chime')).not.toThrow()
  })
})
