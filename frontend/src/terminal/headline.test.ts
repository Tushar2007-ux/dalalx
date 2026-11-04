import { describe, expect, it } from 'vitest'
import { priceHeadline } from './headline'

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
