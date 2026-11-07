import { describe, expect, it } from 'vitest'
import { shouldPlayIntro } from './PressIntro'

describe('the opening animation', () => {
  it('plays once, on ordinary pages', () => {
    expect(shouldPlayIntro('#/', false, false)).toBe(true)
    expect(shouldPlayIntro('#/floor', false, false)).toBe(true)
    expect(shouldPlayIntro('#/', true, false)).toBe(false)
  })

  it('never plays for reduced motion, the demo, the big board or phones joining', () => {
    expect(shouldPlayIntro('#/', false, true)).toBe(false)
    expect(shouldPlayIntro('#/demo', false, false)).toBe(false)
    expect(shouldPlayIntro('#/wall', false, false)).toBe(false)
    expect(shouldPlayIntro('#/join', false, false)).toBe(false)
  })
})
