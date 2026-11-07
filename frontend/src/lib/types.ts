/**
 * The JSON shapes the Spring Boot server sends. They mirror the Java records
 * in core (BookSnapshot, Trade, Candle, OrderView) and server (Dtos).
 * Prices are always whole paise (₹102.50 → 10250), exactly as in Java.
 */

export type Side = 'BUY' | 'SELL'
export type OrderType = 'LIMIT' | 'MARKET' | 'IOC'
export type OrderStatus = 'NEW' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED' | 'REJECTED'

export type MarketPhase = 'OPEN' | 'HALTED' | 'AUCTION'

/** A stock's trading phase; reason and until are null while it is simply open. */
export interface MarketStatus {
  phase: MarketPhase
  reason: string | null
  until: string | null
  /** When this phase began (null if the stock has been open all session). */
  since?: string | null
}

export interface SymbolInfo extends MarketStatus {
  symbol: string
  name: string
  referencePrice: number
  lastPrice: number
  /** The latest indicative auction state, while the stock is in a call auction. */
  auction?: AuctionUpdate | null
}

/** Pushed on /topic/auction during a call auction: where it would trade if it closed now. */
export interface AuctionUpdate {
  symbol: string
  timestamp: string
  /** Indicative price in paise, or null if no bid and ask overlap yet. */
  price: number | null
  volume: number
  buyQty: number
  sellQty: number
  endsAt: string | null
}

/** Pushed on /topic/status when a stock is halted or reopens. */
export interface MarketStatusChanged extends MarketStatus {
  symbol: string
  timestamp: string
}

export interface Level {
  price: number
  totalQty: number
  orderCount: number
  /** The trader with the most shares at this price (X-Ray mode shows their stamp). */
  mainTrader?: string | null
}

export interface BookSnapshot {
  symbol: string
  bids: Level[]
  asks: Level[]
}

export interface Trade {
  id: number
  symbol: string
  price: number
  qty: number
  buyOrderId: number
  sellOrderId: number
  buyerId: string
  sellerId: string
  aggressorSide: Side
  timestamp: string
}

export interface Candle {
  startMillis: number
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export interface OrderView {
  id: number
  symbol: string
  traderId: string
  side: Side
  type: OrderType
  limitPrice: number
  originalQty: number
  remainingQty: number
  status: OrderStatus
}

export interface PlaceOrder {
  symbol: string
  traderId?: string
  side: Side
  type: OrderType
  price: number
  qty: number
}

export interface OrderResult {
  order: OrderView
  trades: Trade[]
  rested: boolean
  reason: string | null
}

export interface CancelResponse {
  orderId: number
  cancelled: boolean
  order: OrderView | null
  reason: string | null
}

/** One line of /topic/orders: something happened to an order. */
export interface OrderActivity {
  kind: 'ACCEPTED' | 'REJECTED' | 'CANCELLED'
  symbol: string
  orderId: number
  traderId: string
  side: Side
  type: OrderType
  price: number
  qty: number
  remainingQty: number
  reason: string | null
}

export type Interval = '10s' | '1m' | '5m' | '15m' | '1D'
export const INTERVALS: Interval[] = ['10s', '1m', '5m', '15m', '1D']

/** A simulated trader, from GET /api/bots. P&L and cash are paise; positions are shares. */
export interface BotInfo {
  id: string
  name: string
  role: string
  line: string
  tuning: { aggressiveness: number; size: number }
  status: 'ACTIVE' | 'PAUSED'
  running: boolean
  stats: {
    traderId: string
    orders: number
    trades: number
    volume: number
    cash: number
    positions: Record<string, number>
    pnl: number
  }
}

/** One item on The Wire (GET /api/wire, pushed on /topic/wire). */
export interface WireHeadline {
  id: number
  time: string
  kicker: string
  symbol: string | null
  text: string
  deck: string | null
  tone: 'UP' | 'DOWN' | 'ALERT' | 'NEUTRAL'
}

/** A storyline from GET /api/scenarios. */
export interface Story {
  id: string
  kicker: string
  headline: string
  deck: string
  symbol: string
  tone: 'UP' | 'DOWN' | 'NEUTRAL'
  durationSeconds: number
}

export interface Storylines {
  stories: Story[]
  running: { id: string | null; startedAt: string | null; durationSeconds: number | null }
}

export type BotStyle = 'CONTRARIAN' | 'MOMENTUM' | 'MARKET_MAKER' | 'NOISE'

/** GET /api/xray/latest: how one of your orders was matched, step by step. */
export interface XRayLevel {
  price: number
  orders: { orderId: number; traderId: string; remainingQty: number }[]
}

export interface XRayFill {
  price: number
  qty: number
  counterparty: string
  restingOrderId: number
  /** Index into bookBefore (0 = best). */
  level: number
}

export interface XRayStep {
  kind: 'ARRIVE' | 'CHECK' | 'FILL' | 'STOP' | 'OUTCOME'
  title: string
  body: string
}

export interface XRay {
  orderId: number
  symbol: string
  traderId: string
  side: Side
  type: OrderType
  limitPrice: number
  qty: number
  time: string
  bookBefore: XRayLevel[]
  fills: XRayFill[]
  status: OrderStatus
  remaining: number
  reason: string | null
  steps: XRayStep[]
}

/** One order's life in a surveillance case's evidence. */
export interface OrderLife {
  orderId: number
  traderId: string
  symbol: string
  side: Side
  price: number
  qty: number
  placedAt: string
  endedAt: string | null
  outcome: 'LIVE' | 'FILLED' | 'CANCELLED'
  filled: number
}

export type Severity = 'NOTICE' | 'WARNING' | 'CRITICAL'

/** A surveillance case (GET /api/surveillance/cases, pushed on /topic/surveillance). */
export interface Case {
  id: string
  ruleId: string
  ruleName: string
  severity: Severity
  traderId: string
  symbol: string
  openedAt: string
  summary: string
  figures: Record<string, string>
  notes: string[]
  evidence: OrderLife[]
  flagged: number[]
  status: 'OPEN' | 'DISMISSED' | 'SUSPENDED'
}

export interface RuleInfo {
  id: string
  name: string
  threshold: string
  severity: Severity
  enabled: boolean
}

/** Where a worker's command came from (Engine Room colours). Mirrors core CommandSource. */
export type CommandSource = 'PRESENTER' | 'AUDIENCE' | 'BOT' | 'CLOCK' | 'SYSTEM'
export const COMMAND_SOURCES: CommandSource[] = ['PRESENTER', 'AUDIENCE', 'BOT', 'CLOCK', 'SYSTEM']

/** How hard one worker thread worked over the last sample. Mirrors core WorkerLoad. */
export interface WorkerLoad {
  symbol: string
  thread: string
  state: string
  running: string | null
  pending: number
  capacity: number
  processed: number
  perSecond: number
  /** 0.0–1.0: share of the interval the thread spent running commands. */
  utilisation: number
  arrivalsPerSecond: Partial<Record<CommandSource, number>>
}

/** Pushed on /topic/engine about four times a second. */
export interface EngineSnapshot {
  at: string
  workers: WorkerLoad[]
}

/** POST /api/engine/rush */
export interface RushResult {
  producers: number
  commands: number
  millis: number
}

export type EntrantKind = 'PRESENTER' | 'AUDIENCE' | 'BOT'

/** One leaderboard row. */
export interface Standing {
  rank: number
  traderId: string
  name: string
  kind: EntrantKind
  pnl: number
  trades: number
  volume: number
}

/** Pushed on /topic/leaderboard once a second. */
export interface Board {
  standings: Standing[]
  guests: number
}

/** One open position, with average cost (paise). */
export interface Holding {
  symbol: string
  qty: number
  avgPrice: number
  lastPrice: number
  value: number
  unrealised: number
}

/** A trader's scorecard (same shape as a bot's stats). */
export interface TraderStats {
  traderId: string
  orders: number
  trades: number
  volume: number
  cash: number
  positions: Record<string, number>
  pnl: number
}

export interface Portfolio {
  stats: TraderStats
  holdings: Holding[]
}

/** The answer to joining from a phone. Keep the token secret. */
export interface Joined {
  traderId: string
  name: string
  token: string
}

export interface GuestView {
  traderId: string
  name: string
  stats: TraderStats
  holdings: Holding[]
  rank: number
  of: number
}

export interface Invite {
  addresses: string[]
  guests: number
}

export interface GuestOrder {
  symbol: string
  side: Side
  type: 'MARKET' | 'LIMIT'
  price?: number
  qty: number
}
