/**
 * The JSON shapes the Spring Boot server sends. They mirror the Java records
 * in core (BookSnapshot, Trade, Candle, OrderView) and server (Dtos).
 * Prices are always whole paise (₹102.50 → 10250), exactly as in Java.
 */

export type Side = 'BUY' | 'SELL'
export type OrderType = 'LIMIT' | 'MARKET' | 'IOC'
export type OrderStatus = 'NEW' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED' | 'REJECTED'

export interface SymbolInfo {
  symbol: string
  name: string
  referencePrice: number
  lastPrice: number
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
