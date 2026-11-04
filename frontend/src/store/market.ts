import { create } from 'zustand'
import {
  addTradeToCandles, applyFill, filledBy, isWorking, ME,
} from '../lib/market'
import { formatPaise, formatQty } from '../lib/format'
import type {
  BookSnapshot, Candle, Interval, OrderActivity, OrderView, SymbolInfo, Trade, WireHeadline,
} from '../lib/types'

/**
 * The one place the terminal's state lives (a zustand store).
 *
 * Components read slices of it with `useMarket(s => s.x)` and re-render only
 * when their slice changes. Live updates arrive through the `apply*` actions,
 * which the stream calls at most once per animation frame.
 */

export type Connection = 'connecting' | 'live' | 'offline'
export type Edition = 'day' | 'night'

export interface ToastItem {
  id: number
  kind: 'filled' | 'partial' | 'rejected' | 'cancelled' | 'info'
  title: string
  detail?: string
  time?: string
}

/** Most trades kept on the tape. */
export const TAPE_SIZE = 40
const MAX_TOASTS = 3
/** Headlines kept for The Wire. */
export const WIRE_SIZE = 20

export interface MarketState {
  connection: Connection
  symbols: SymbolInfo[]
  selected: string
  interval: Interval
  edition: Edition
  xray: boolean
  lastPrice: Record<string, number>
  books: Record<string, BookSnapshot>
  /** Newest-first trades per symbol. */
  tapes: Record<string, Trade[]>
  /** The chart's candles: one series, for `${symbol}:${interval}` (switching refetches). */
  chart: { key: string; candles: Candle[] } | null
  myOrders: Record<number, OrderView>
  toasts: ToastItem[]
  /** The Wire, newest first. */
  wire: WireHeadline[]
  /** Price (paise) the ticket should show after a click on a book level; bumped `nonce` re-applies the same price. */
  ticketPrefill: { price: number; nonce: number } | null

  setConnection: (c: Connection) => void
  setSymbols: (symbols: SymbolInfo[]) => void
  select: (symbol: string) => void
  setInterval: (interval: Interval) => void
  setEdition: (edition: Edition) => void
  setXray: (on: boolean) => void
  setCandles: (symbol: string, interval: Interval, candles: Candle[]) => void
  setTape: (symbol: string, trades: Trade[]) => void
  applyBook: (book: BookSnapshot) => void
  applyTrades: (symbol: string, trades: Trade[]) => void
  applyActivity: (activity: OrderActivity[]) => void
  trackOrder: (order: OrderView) => void
  toast: (toast: Omit<ToastItem, 'id'>) => void
  dismissToast: (id: number) => void
  prefillTicket: (price: number) => void
  setWire: (wire: WireHeadline[]) => void
  applyHeadline: (headline: WireHeadline) => void
}

let nextToastId = 1

export const chartKey = (symbol: string, interval: Interval) => `${symbol}:${interval}`

function savedEdition(): Edition {
  try {
    return localStorage.getItem('dalalx-edition') === 'night' ? 'night' : 'day'
  } catch {
    return 'day'
  }
}

/** Toasts for any of my resting orders that this batch of trades filled. */
function fillToasts(before: Record<number, OrderView>, trades: Trade[]): Omit<ToastItem, 'id'>[] {
  const toasts: Omit<ToastItem, 'id'>[] = []
  for (const trade of trades) {
    const mine = before[trade.buyOrderId] ?? before[trade.sellOrderId]
    // Only resting orders: fills of an order I just sent are reported by the REST reply.
    if (!mine || mine.traderId !== ME || trade.aggressorSide === mine.side) continue
    toasts.push({
      kind: 'filled',
      title: `${mine.side === 'BUY' ? 'Bought' : 'Sold'} ${formatQty(trade.qty)} ${trade.symbol} at ₹${formatPaise(trade.price)}`,
      detail: `Resting order #${mine.id} was hit`,
    })
  }
  return toasts
}

export const useMarket = create<MarketState>()((set, get) => ({
  connection: 'connecting',
  symbols: [],
  selected: 'RELIANCE',
  interval: '1m',
  edition: savedEdition(),
  xray: false,
  lastPrice: {},
  books: {},
  tapes: {},
  chart: null,
  myOrders: {},
  toasts: [],
  ticketPrefill: null,
  wire: [],

  setConnection: (connection) => set({ connection }),
  setSymbols: (symbols) => set((s) => ({
    symbols,
    lastPrice: Object.fromEntries(symbols.map((x) => [x.symbol, s.lastPrice[x.symbol] ?? x.lastPrice])),
    selected: symbols.some((x) => x.symbol === s.selected) ? s.selected : symbols[0]?.symbol ?? s.selected,
  })),
  select: (selected) => set({ selected }),
  setInterval: (interval) => set({ interval }),
  setEdition: (edition) => {
    try { localStorage.setItem('dalalx-edition', edition) } catch { /* private mode: not remembered */ }
    set({ edition })
  },
  setXray: (xray) => set({ xray }),
  setCandles: (symbol, interval, candles) => set({ chart: { key: chartKey(symbol, interval), candles } }),
  setTape: (symbol, trades) => set((s) => ({ tapes: { ...s.tapes, [symbol]: trades.slice(0, TAPE_SIZE) } })),

  applyBook: (book) => set((s) => ({ books: { ...s.books, [book.symbol]: book } })),

  applyTrades: (symbol, trades) => {
    if (!trades.length) return
    const s = get()
    const newestFirst = [...trades].reverse()
    const myOrders = { ...s.myOrders }
    for (const trade of trades) {
      for (const id of [trade.buyOrderId, trade.sellOrderId]) {
        const order = myOrders[id]
        // Only passive fills: the REST reply already counted fills of the order I just sent.
        if (order && isWorking(order) && trade.aggressorSide !== order.side) {
          myOrders[id] = applyFill(order, filledBy(order, trade))
        }
      }
    }
    let chart = s.chart
    if (chart && chart.key === chartKey(symbol, s.interval)) {
      let candles = chart.candles
      for (const trade of trades) candles = addTradeToCandles(candles, trade, s.interval)
      chart = { key: chart.key, candles }
    }
    set({
      lastPrice: { ...s.lastPrice, [symbol]: trades[trades.length - 1].price },
      tapes: { ...s.tapes, [symbol]: [...newestFirst, ...(s.tapes[symbol] ?? [])].slice(0, TAPE_SIZE) },
      chart,
      myOrders,
    })
    fillToasts(s.myOrders, trades).forEach(get().toast)
  },

  applyActivity: (activity) => set((s) => {
    const myOrders = { ...s.myOrders }
    for (const a of activity) {
      const order = myOrders[a.orderId]
      if (order && a.kind === 'CANCELLED') myOrders[a.orderId] = { ...order, status: 'CANCELLED', remainingQty: a.remainingQty }
    }
    return { myOrders }
  }),

  trackOrder: (order) => set((s) => {
    // If the stream already reported a later fill, keep that newer, smaller remainder.
    const known = s.myOrders[order.id]
    const latest = known && known.remainingQty < order.remainingQty ? known : order
    return { myOrders: { ...s.myOrders, [order.id]: latest } }
  }),

  toast: (toast) => set((s) => ({
    toasts: [{ ...toast, id: nextToastId++ }, ...s.toasts].slice(0, MAX_TOASTS),
  })),
  dismissToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
  prefillTicket: (price) => set((s) => ({ ticketPrefill: { price, nonce: (s.ticketPrefill?.nonce ?? 0) + 1 } })),
  // Merge rather than replace: a pushed headline may arrive before the REST snapshot does.
  setWire: (wire) => set((s) => {
    const byId = new Map([...wire, ...s.wire].map((h) => [h.id, h]))
    return { wire: [...byId.values()].sort((a, b) => b.id - a.id).slice(0, WIRE_SIZE) }
  }),
  applyHeadline: (headline) => set((s) => (s.wire.some((h) => h.id === headline.id)
    ? s
    : { wire: [headline, ...s.wire].slice(0, WIRE_SIZE) })),
}))
