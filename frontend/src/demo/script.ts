import { api } from '../lib/api'
import { formatPaise, formatQty } from '../lib/format'
import type { Route } from '../shell/route'
import type { MarketState } from '../store/market'
import { outcomeToast } from '../terminal/ticket'

/**
 * The guided tour, as data: each step names a page, a caption to print in the
 * demo bar, an optional action to run once when the step begins, and how long
 * to stay. A step can also wait for something to happen in the market (the
 * breaker tripping, the auction ending), with a maximum so the tour never
 * stalls. Keeping the tour as plain data makes it easy to read and change.
 */
export interface DemoStep {
  route: Exclude<Route, 'demo'>
  title: string
  caption: string
  /** Minimum seconds on this step. */
  seconds: number
  /** If given, the step ends as soon as this is true (after `seconds`), or after `maxSeconds`. */
  until?: (s: MarketState) => boolean
  maxSeconds?: number
  action?: (s: MarketState) => Promise<unknown> | void
}

const STOCK = 'RELIANCE'
const phase = (s: MarketState) => s.status[STOCK]?.phase ?? 'OPEN'

/** Buys 25 RELIANCE at the best ask (so it trades at once), the way a person would from the ticket. */
async function placeDemoOrder(s: MarketState) {
  const best = s.books[STOCK]?.asks[0]?.price ?? s.lastPrice[STOCK]
  if (!best) return
  const result = await api.placeOrder({ symbol: STOCK, side: 'BUY', type: 'LIMIT', price: best + 50, qty: 25 })
  s.trackOrder(result.order)
  s.toast({ ...outcomeToast(result), detail: `Demo order · limit ₹${formatPaise(best + 50)} · ${formatQty(25)} shares` })
}

/** Opens the newest case against The Spoofer in the Investigations page. */
async function showTheSpoofersCase(s: MarketState) {
  void api.stopScenario()
  const cases = await api.cases()
  s.setCases(cases)
  const spoof = cases.find((c) => c.traderId === 'SPOOFER' && c.status === 'OPEN') ?? cases.find((c) => c.traderId === 'SPOOFER')
  s.setFocusCase(spoof?.id ?? null)
}

async function suspendTheSpoofer(s: MarketState) {
  const focus = s.cases.find((c) => c.id === s.focusCase)
  const target = focus?.status === 'OPEN' ? focus : (await api.cases()).find((c) => c.traderId === 'SPOOFER' && c.status === 'OPEN')
  if (target) {
    s.applyCase(await api.suspendCase(target.id))
    s.setFocusCase(target.id)
  }
}

export const DEMO: DemoStep[] = [
  {
    route: 'front', title: 'The front page', seconds: 8,
    caption: 'DalalX is a simulated stock exchange: a real matching engine in pure Java, five stocks each matched on its own thread, and a newspaper on top.',
  },
  {
    route: 'floor', title: 'The trading floor', seconds: 12,
    caption: 'Every price, candle and line of the tape is real matching, driven by five trading bots. The Wire prints the news as it happens.',
    action: (s) => { s.select(STOCK); s.setXray(false) },
  },
  {
    route: 'floor', title: 'Placing an order', seconds: 8,
    caption: 'A limit order to buy 25 RELIANCE. It trades at once against the best asks, at their prices: best price first, then first come, first served.',
    action: placeDemoOrder,
  },
  {
    route: 'xray', title: 'X-Ray', seconds: 16,
    caption: 'X-Ray replays that order against the book exactly as it stood when the order arrived, fill by fill, with whose orders it met.',
  },
  {
    route: 'floor', title: 'Who is behind each price', seconds: 8,
    caption: 'X-Ray mode stamps every level of the book with the trader holding the most shares there.',
    action: (s) => s.setXray(true),
  },
  {
    route: 'correspondents', title: 'The correspondents', seconds: 10,
    caption: 'Five bots, each a Strategy behind one interface: a market maker, a trend follower, a panic seller, a block trader and a spoofer.',
    action: (s) => s.setXray(false),
  },
  {
    route: 'floor', title: 'Flash crash', seconds: 10, maxSeconds: 90, until: (s) => phase(s) === 'HALTED',
    caption: 'Storyline: Panic Seller dumps RELIANCE into a thin book. Watch the price slide…',
    action: async (s) => { s.select(STOCK); await api.startScenario('flash-crash') },
  },
  {
    route: 'floor', title: 'Circuit breaker', seconds: 6, maxSeconds: 40, until: (s) => phase(s) === 'AUCTION',
    caption: 'Ten per cent in sixty seconds: the circuit breaker halts RELIANCE (the State pattern: OPEN → HALTED). New orders are refused; cancels still work.',
  },
  {
    route: 'floor', title: 'Call auction', seconds: 6, maxSeconds: 40, until: (s) => phase(s) === 'OPEN',
    caption: 'The call auction collects orders without matching and publishes an indicative price. At the bell, everything that crosses trades at one price.',
  },
  {
    route: 'floor', title: 'Trading resumes', seconds: 6,
    caption: 'Reopened at the auction price. The Wire has printed every step of the story.',
  },
  {
    route: 'surveillance', title: 'Investigations', seconds: 12,
    caption: 'Meanwhile the Surveillance Inspector caught The Spoofer: large bids stacked and pulled within a quarter-second. Here is the evidence.',
    action: showTheSpoofersCase,
  },
  {
    route: 'surveillance', title: 'The ruling', seconds: 8,
    caption: 'Suspended: its orders are now refused on every stock, and everything it had resting has been cancelled.',
    action: suspendTheSpoofer,
  },
  {
    route: 'front', title: 'That’s DalalX', seconds: 6, action: (s) => s.setFocusCase(null),
    caption: 'Matching, concurrency, bots, a circuit breaker, a call auction, X-Ray and surveillance. Thank you for reading.',
  },
]

/** Total length of the tour if every waiting step takes its minimum. */
export const DEMO_MIN_SECONDS = DEMO.reduce((sum, step) => sum + step.seconds, 0)
