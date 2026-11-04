import { describe, expect, it } from 'vitest'
import { bannerHeadline, priceHeadline } from './headline'

describe('priceHeadline', () => {
  it('writes the move against the reference price', () => {
    expect(priceHeadline('TCS', 449900, 409000, 'live')).toMatchObject({ tone: 'up', headline: 'TCS rises 10.00% to ₹4,499.00' })
    expect(priceHeadline('TCS', 400000, 409000, 'live').tone).toBe('down')
    expect(priceHeadline('TCS', 409000, 409000, 'live').tone).toBe('neutral')
  })

  it('prints a market-closed story when the exchange is offline', () => {
    expect(priceHeadline('TCS', 409000, 409000, 'offline')).toMatchObject({ kicker: 'Market Closed', state: 'stale' })
  })
})

describe('bannerHeadline', () => {
  const now = Date.parse('2026-11-04T09:20:00Z')
  const story = (symbol: string | null, secondsAgo: number) => ({
    id: 1, time: new Date(now - secondsAgo * 1000).toISOString(), kicker: 'Block trade', symbol,
    text: 'The Whale sells a block', deck: null, tone: 'DOWN' as const,
  })

  it('leads with fresh news about this stock or the whole market', () => {
    expect(bannerHeadline([story('TCS', 10)], 'TCS', 409000, 409000, 'live', now))
      .toMatchObject({ kicker: 'Block trade', headline: 'The Whale sells a block', tone: 'down' })
    expect(bannerHeadline([story(null, 10)], 'TCS', 409000, 409000, 'live', now).kicker).toBe('Block trade')
  })

  it('falls back to the price story for stale or unrelated news', () => {
    expect(bannerHeadline([story('TCS', 600)], 'TCS', 409000, 409000, 'live', now).kicker).toBe('Latest')
    expect(bannerHeadline([story('INFY', 5)], 'TCS', 409000, 409000, 'live', now).kicker).toBe('Latest')
  })
})
