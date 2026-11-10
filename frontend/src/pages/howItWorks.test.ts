import { describe, expect, it } from 'vitest'
import { START_BOOK, match, queueFor } from './howItWorks'

describe('the explainer’s toy engine', () => {
  it('queues the best price first, then whoever arrived first', () => {
    // Two sellers at ₹102: The Whale arrived before Momentum, so it trades first.
    expect(queueFor(START_BOOK, 'SELL').map((o) => [o.price, o.who]))
      .toEqual([[102, 'The Whale'], [102, 'Momentum'], [103, 'The Munim']])
    expect(queueFor(START_BOOK, 'BUY').map((o) => o.price)).toEqual([101, 100, 99])
  })

  it('a limit buy takes what it can and rests the remainder', () => {
    const r = match(START_BOOK, 'BUY', 'LIMIT', 100, 102)

    expect(r.fills).toEqual([
      { against: 2, price: 102, qty: 30, who: 'The Whale' },
      { against: 3, price: 102, qty: 20, who: 'Momentum' },
    ])
    expect(r.leftover).toBe(50)
    expect(r.rest).toBe('rests')
    expect(r.book.find((o) => o.who === 'You')).toMatchObject({ price: 102, qty: 50, side: 'BUY' })
    expect(r.story.at(-1)).toContain('rest in the book')
  })

  it('an IOC buy cancels what it cannot trade now', () => {
    const r = match(START_BOOK, 'BUY', 'IOC', 100, 102)

    expect(r.fills).toHaveLength(2)
    expect(r.leftover).toBe(50)
    expect(r.rest).toBe('cancelled')
    expect(r.book.some((o) => o.who === 'You')).toBe(false)
  })

  it('a market buy walks up the book regardless of price', () => {
    const r = match(START_BOOK, 'BUY', 'MARKET', 100, 0)

    expect(r.fills.map((f) => f.price)).toEqual([102, 102, 103])
    expect(r.fills.at(-1)!.qty).toBe(40)
    expect(r.leftover).toBe(10)
    expect(r.rest).toBe('cancelled')
  })

  it('stops at the limit and says so', () => {
    const r = match(START_BOOK, 'BUY', 'LIMIT', 100, 102.5)

    expect(r.fills.map((f) => f.price)).toEqual([102, 102])
    expect(r.story.join(' ')).toContain('Matching stops here')
  })

  it('gives the price improvement to the incoming order', () => {
    const r = match(START_BOOK, 'BUY', 'LIMIT', 10, 105)

    expect(r.fills[0].price).toBe(102)            // not the 105 they were willing to pay
    expect(r.story[1]).toContain('improvement is yours')
  })

  it('rejects a market order when the other side is empty', () => {
    const r = match(START_BOOK.filter((o) => o.side === 'BUY'), 'BUY', 'MARKET', 10, 0)

    expect(r.rest).toBe('rejected')
    expect(r.fills).toEqual([])
    expect(r.story.at(-1)).toContain('rejected')
  })

  it('matches a sell down the bids the same way', () => {
    const r = match(START_BOOK, 'SELL', 'LIMIT', 40, 100)

    expect(r.fills).toEqual([
      { against: 4, price: 101, qty: 25, who: 'The Munim' },
      { against: 5, price: 100, qty: 15, who: 'Panic Seller' },
    ])
    expect(r.leftover).toBe(0)
    expect(r.rest).toBe('none')
  })

  it('never changes the book it was given', () => {
    const before = JSON.stringify(START_BOOK)
    match(START_BOOK, 'BUY', 'MARKET', 200, 0)
    expect(JSON.stringify(START_BOOK)).toBe(before)
  })
})
