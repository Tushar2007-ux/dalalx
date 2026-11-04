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

export type Interval = '1m' | '5m' | '15m' | '1D'
export const INTERVALS: Interval[] = ['1m', '5m', '15m', '1D']

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
