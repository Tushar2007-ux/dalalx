import { describe, expect, it } from 'vitest'
import { VIVA_NOTES } from './notes'

describe('viva notes', () => {
  it('every note names a class, an idea and something to say', () => {
    for (const [key, n] of Object.entries(VIVA_NOTES)) {
      expect(n.cls.length, key).toBeGreaterThan(3)
      expect(n.idea.length, key).toBeGreaterThan(3)
      expect(n.say.split('. ').length, key).toBeLessThanOrEqual(3)
    }
  })

  it('covers the parts examiners point at', () => {
    for (const key of ['book', 'ticket', 'halt', 'auction', 'engine', 'surveillance']) {
      expect(VIVA_NOTES).toHaveProperty(key)
    }
  })
})
