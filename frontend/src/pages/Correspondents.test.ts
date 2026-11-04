import { describe, expect, it } from 'vitest'
import { monogramOf } from '../lib/monogram'
import { storyMeta } from './Correspondents'

describe('correspondents helpers', () => {
  it('makes a two-letter stamp from a byline', () => {
    expect(monogramOf('The Contrarian')).toBe('CO')
    expect(monogramOf('noise 9')).toBe('NO')
    expect(monogramOf('   ')).toBe('??')
  })

  it('describes how long a story runs and where', () => {
    const story = { id: 'x', kicker: '', headline: '', deck: '', tone: 'DOWN' as const }
    expect(storyMeta({ ...story, symbol: 'RELIANCE', durationSeconds: 90 })).toBe('1.5 min · RELIANCE')
    expect(storyMeta({ ...story, symbol: 'ALL', durationSeconds: 45 })).toBe('45 s · All stocks')
  })
})
