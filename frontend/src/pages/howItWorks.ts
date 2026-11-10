/**
 * A toy matching engine for the How It Works page.
 *
 * It follows exactly the rules the Java engine follows — best price first,
 * then earliest first, trading at the resting order's price — on a book of
 * six orders, so a reader can try LIMIT, MARKET and IOC and watch what
 * happens. It is pure and runs in the browser, so the page explains the
 * exchange even when the exchange isn't running.
 */

export type Side = 'BUY' | 'SELL'
export type Kind = 'LIMIT' | 'MARKET' | 'IOC'

/** One resting order in the toy book. `at` is arrival order, lowest first. */
export interface Resting {
  id: number
  side: Side
  price: number
  qty: number
  who: string
  at: number
}

export interface Fill {
  against: number
  price: number
  qty: number
  who: string
}

export interface Outcome {
  fills: Fill[]
  /** Shares that found nobody to trade with. */
  leftover: number
  /** What happened to the leftover. */
  rest: 'rests' | 'cancelled' | 'rejected' | 'none'
  book: Resting[]
  /** One line per thing that happened, in the newspaper's voice. */
  story: string[]
}

/** The starting book: three sellers above, three buyers below, in rupees. */
export const START_BOOK: Resting[] = [
  { id: 1, side: 'SELL', price: 103, qty: 40, who: 'The Munim', at: 1 },
  { id: 2, side: 'SELL', price: 102, qty: 30, who: 'The Whale', at: 2 },
  { id: 3, side: 'SELL', price: 102, qty: 20, who: 'Momentum', at: 3 },
  { id: 4, side: 'BUY', price: 101, qty: 25, who: 'The Munim', at: 4 },
  { id: 5, side: 'BUY', price: 100, qty: 50, who: 'Panic Seller', at: 5 },
  { id: 6, side: 'BUY', price: 99, qty: 60, who: 'The Whale', at: 6 },
]

/**
 * Sorts one side of the book the way the engine does: best price first, and
 * among equal prices, whoever arrived first.
 */
export function queueFor(book: Resting[], side: Side): Resting[] {
  return book
    .filter((o) => o.side === side)
    .sort((a, b) => (side === 'SELL' ? a.price - b.price : b.price - a.price) || a.at - b.at)
}

/**
 * Matches one order against the book.
 *
 * @param kind  LIMIT rests what it can't trade, IOC cancels it, MARKET takes
 *              any price but is rejected outright if the book is empty
 * @param limit ignored for MARKET
 */
export function match(book: Resting[], side: Side, kind: Kind, qty: number, limit: number): Outcome {
  const opposite = queueFor(book, side === 'BUY' ? 'SELL' : 'BUY')
  const story: string[] = []
  const fills: Fill[] = []
  let left = qty
  const verb = side === 'BUY' ? 'buy' : 'sell'

  story.push(kind === 'MARKET'
    ? `Your order arrives: ${verb} ${qty} at whatever the market asks.`
    : `Your order arrives: ${verb} ${qty} at ${side === 'BUY' ? 'no more than' : 'no less than'} ₹${limit}.`)

  if (kind === 'MARKET' && opposite.length === 0) {
    story.push('There is nobody on the other side, and a market order has no price to rest at, so it is rejected.')
    return { fills: [], leftover: qty, rest: 'rejected', book, story }
  }

  const after = book.map((o) => ({ ...o }))
  for (const level of opposite) {
    if (left === 0) break
    const inReach = kind === 'MARKET' || (side === 'BUY' ? level.price <= limit : level.price >= limit)
    if (!inReach) {
      story.push(`The next ${side === 'BUY' ? 'seller' : 'buyer'} wants ₹${level.price}, which is ${side === 'BUY' ? 'above' : 'below'} your limit. Matching stops here.`)
      break
    }
    const traded = Math.min(left, level.qty)
    fills.push({ against: level.id, price: level.price, qty: traded, who: level.who })
    story.push(`${traded} shares trade at ₹${level.price} against ${level.who}` +
      (kind !== 'MARKET' && ((side === 'BUY' && level.price < limit) || (side === 'SELL' && level.price > limit))
        ? ` — better than your limit of ₹${limit}, and the improvement is yours.`
        : '.'))
    left -= traded
    const row = after.find((o) => o.id === level.id)!
    row.qty -= traded
  }

  const bookAfter = after.filter((o) => o.qty > 0)
  if (left === 0) {
    story.push('Filled in full.')
    return { fills, leftover: 0, rest: 'none', book: bookAfter, story }
  }
  if (kind === 'LIMIT') {
    story.push(`The remaining ${left} rest in the book at ₹${limit}, waiting for someone to meet them.`)
    bookAfter.push({ id: 99, side, price: limit, qty: left, who: 'You', at: 99 })
    return { fills, leftover: left, rest: 'rests', book: bookAfter, story }
  }
  story.push(kind === 'IOC'
    ? `The remaining ${left} are cancelled: that is what the "or cancel" in Immediate-or-Cancel means.`
    : `The remaining ${left} are cancelled: a market order never rests in the book.`)
  return { fills, leftover: left, rest: 'cancelled', book: bookAfter, story }
}
