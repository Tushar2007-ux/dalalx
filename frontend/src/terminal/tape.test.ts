import { describe, expect, it } from 'vitest'
import { isBlockTrade } from './tape'

describe('block trades', () => {
  it('are big by shares or by value', () => {
    expect(isBlockTrade({ price: 246000, qty: 10 })).toBe(false)
    expect(isBlockTrade({ price: 100, qty: 1000 })).toBe(true)
    expect(isBlockTrade({ price: 409000, qty: 250 })).toBe(true) // ₹10.2 lakh
  })
})
