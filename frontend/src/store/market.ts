import { create } from 'zustand'
import {
  addTradeToCandles, applyFill, filledBy, isWorking, ME,
} from '../lib/market'
import { formatPaise, formatQty } from '../lib/format'
import type {
  Board, EngineSnapshot,
  AuctionUpdate, BookSnapshot, Candle, Case, Interval, MarketStatus, MarketStatusChanged, OrderActivity, OrderView, SymbolInfo, Trade,
  WireHeadline,
} from '../lib/types'

/**
 * The one place the terminal's state lives (a zustand store).
 *
 * Components read slices of it with `useMarket(s => s.x)` and re-render only
 * when their slice changes. Live updates arrive through the `apply*` actions,
 * which the stream calls at most once per animation frame.
 */

export type Connection = 'connecting' | 'live' | 'offline'

/** A full-screen "big moment": a halt, the reopening bell, or a trader caught. */
export interface Moment {
  id: number
  kind: 'extra' | 'bell' | 'caught'
  kicker: string
  headline: string
  deck?: string
  /** Whose portrait to show for 'caught' (a trader id). */
  traderId?: string
}

/** A trader's most recent trade, for the "on the floor now" strip. */
export interface LastTrade {
  at: number
  symbol: string
  side: 'BUY' | 'SELL'
  qty: number
}
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
  /** The big moment on screen, if any. */
  moment: Moment | null
  /** Each trader's latest trade (both sides of every trade count). */
  lastTradeBy: Record<string, LastTrade>
  /** The price before the last change, per stock (the ticker flashes up or down from it). */
  tickerPrev: Record<string, number>
  /** Each stock's trading phase (open, halted by the circuit breaker, auction). */
  status: Record<string, MarketStatus>
  /** The latest indicative state of each stock's call auction (only while one runs). */
  auctions: Record<string, AuctionUpdate>
  /** Surveillance cases, newest first. */
  cases: Case[]
  /** A case the Investigations page should open (set by links and the demo); null = newest. */
  focusCase: string | null
  /** The Wire, newest first. */
  wire: WireHeadline[]
  /** The latest Engine Room measurement. */
  engine: EngineSnapshot | null
  /** The latest leaderboard (presenter, audience, bots). */
  board: Board | null
  /** Display names for trader ids that aren't bots (audience guests), from the leaderboard. */
  names: Record<string, string>
  /** Viva mode: every panel shows the Java class and pattern behind it. */
  viva: boolean
  /** Presenter mode: larger type for a projector, fewer panels. */
  presenter: boolean
  /** A side chosen by keyboard (B / S) for the ticket to pick up; `nonce` re-applies the same side. */
  ticketSide: { side: 'buy' | 'sell'; nonce: number } | null
  /** Whether the keyboard-shortcut card is open. */
  helpOpen: boolean
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
  applyStatus: (status: MarketStatusChanged) => void
  setCases: (cases: Case[]) => void
  applyCase: (c: Case) => void
  applyAuction: (a: AuctionUpdate) => void
  setFocusCase: (id: string | null) => void
  setViva: (on: boolean) => void
  setPresenter: (on: boolean) => void
  chooseSide: (side: 'buy' | 'sell') => void
  setHelpOpen: (open: boolean) => void
  applyEngine: (e: EngineSnapshot) => void
  applyBoard: (b: Board) => void
  showMoment: (m: Omit<Moment, 'id'>) => void
  clearMoment: () => void
}

let nextToastId = 1

export const chartKey = (symbol: string, interval: Interval) => `${symbol}:${interval}`

/** Reads a remembered on/off setting (false in private mode). */
function savedFlag(key: string): boolean {
  try {
    return localStorage.getItem(key) === '1'
  } catch {
    return false
  }
}

function saveFlag(key: string, on: boolean): void {
  try { localStorage.setItem(key, on ? '1' : '0') } catch { /* not remembered */ }
}

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
  interval: '10s',
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
  moment: null,
  lastTradeBy: {},
  tickerPrev: {},
  status: {},
  cases: [],
  focusCase: null,
  auctions: {},
  engine: null,
  board: null,
  names: {},
  viva: savedFlag('dalalx-viva'),
  presenter: savedFlag('dalalx-presenter'),
  ticketSide: null,
  helpOpen: false,

  setViva: (viva) => { saveFlag('dalalx-viva', viva); set({ viva }) },
  setPresenter: (presenter) => { saveFlag('dalalx-presenter', presenter); set({ presenter }) },
  chooseSide: (side) => set((s) => ({ ticketSide: { side, nonce: (s.ticketSide?.nonce ?? 0) + 1 } })),
  setHelpOpen: (helpOpen) => set({ helpOpen }),

  applyEngine: (engine) => set({ engine }),
  applyBoard: (board) => set((s) => {
    const names = { ...s.names }
    for (const st of board.standings) names[st.traderId] = st.name
    return { board, names }
  }),
  setConnection: (connection) => set({ connection }),
  setSymbols: (symbols) => set((s) => ({
    symbols,
    auctions: Object.fromEntries(symbols.filter((x) => x.auction).map((x) => [x.symbol, x.auction!])),
    status: Object.fromEntries(symbols.map((x) => [x.symbol, { phase: x.phase ?? 'OPEN', reason: x.reason ?? null, until: x.until ?? null, since: x.since ?? null }])),
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
  setTape: (symbol, trades) => set((s) => {
    const byId = new Map([...trades, ...(s.tapes[symbol] ?? [])].map((t) => [t.id, t]))
    return { tapes: { ...s.tapes, [symbol]: [...byId.values()].sort((a, b) => b.id - a.id).slice(0, TAPE_SIZE) } }
  }),

  applyBook: (book) => set((s) => ({ books: { ...s.books, [book.symbol]: book } })),

  applyTrades: (symbol, incoming) => {
    const s = get()
    // The REST snapshot and the stream can overlap by a few trades; never count one twice.
    const known = new Set((s.tapes[symbol] ?? []).map((t) => t.id))
    const trades = incoming.filter((t) => !known.has(t.id))
    if (!trades.length) return
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
    const newPrice = trades[trades.length - 1].price
    const oldPrice = s.lastPrice[symbol]
    const lastTradeBy = { ...s.lastTradeBy }
    const now = Date.now()
    for (const t of trades) {
      lastTradeBy[t.buyerId] = { at: now, symbol, side: 'BUY', qty: t.qty }
      lastTradeBy[t.sellerId] = { at: now, symbol, side: 'SELL', qty: t.qty }
    }
    set({
      lastTradeBy,
      tickerPrev: oldPrice !== undefined && oldPrice !== newPrice ? { ...s.tickerPrev, [symbol]: oldPrice } : s.tickerPrev,
      lastPrice: { ...s.lastPrice, [symbol]: newPrice },
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
  setCases: (cases) => set({ cases }),
  setFocusCase: (focusCase) => set({ focusCase }),
  showMoment: (m) => set({ moment: { ...m, id: nextToastId++ } }),
  clearMoment: () => set({ moment: null }),
  applyAuction: (a) => set((s) => ({ auctions: { ...s.auctions, [a.symbol]: a } })),
  applyCase: (c) => {
    const was = get().cases.find((x) => x.id === c.id)
    const known = was !== undefined
    if (c.status === 'SUSPENDED' && was && was.status !== 'SUSPENDED' && !get().cases.some((x) => x.traderId === c.traderId && x.status === 'SUSPENDED')) {
      get().showMoment({ kind: 'caught', kicker: 'Caught', headline: `${c.traderId === 'SPOOFER' ? 'The Spoofer' : c.traderId} suspended`,
        deck: `${c.ruleName} in ${c.symbol} · case ${c.id}. All resting orders cancelled.`, traderId: c.traderId })
    }
    set((s) => ({ cases: known ? s.cases.map((x) => (x.id === c.id ? c : x)) : [c, ...s.cases] }))
    // Only the serious cases interrupt with a toast; every case is listed under Surveillance and on The Wire.
    if (!known && c.status === 'OPEN' && c.severity === 'CRITICAL') {
      get().toast({ kind: 'info', title: `${c.ruleName} · ${c.symbol}: case ${c.id} opened`, detail: c.summary })
    }
  },
  applyStatus: (st) => {
    const before = get().status[st.symbol]?.phase
    set((s) => ({ status: { ...s.status, [st.symbol]: { phase: st.phase, reason: st.reason, until: st.until, since: st.timestamp } } }))
    if (st.phase === 'HALTED') {
      get().toast({ kind: 'rejected', title: `Trading halted in ${st.symbol}`, detail: st.reason ?? 'Circuit breaker' })
      get().showMoment({ kind: 'extra', kicker: 'Extra · Extra', headline: `Circuit breaker halts ${st.symbol}`,
        deck: st.reason ?? undefined })
    } else if (st.phase === 'AUCTION') {
      get().toast({ kind: 'info', title: `${st.symbol} call auction under way`, detail: 'Limit orders only; everything that crosses trades at one price.' })
    } else if (st.phase === 'OPEN' && before && before !== 'OPEN') {
      const auction = get().auctions[st.symbol]
      if (before === 'AUCTION') {
        get().showMoment({ kind: 'bell', kicker: 'The Bell Rings',
          headline: `${st.symbol} reopens${auction?.price ? ` at ₹${formatPaise(auction.price)}` : ''}`,
          deck: auction?.price ? `${formatQty(auction.volume)} shares crossed at one price` : 'Continuous trading resumes' })
      }
      get().toast({ kind: 'info', title: `Trading resumes in ${st.symbol}${auction?.price ? ` at ₹${formatPaise(auction.price)}` : ''}`,
        detail: auction?.price ? `Call auction crossed ${formatQty(auction.volume)} shares at one price` : undefined })
      set((s) => {
        const { [st.symbol]: _done, ...rest } = s.auctions
        return { auctions: rest }
      })
    }
  },
  setWire: (wire) => set((s) => {
    const byId = new Map([...wire, ...s.wire].map((h) => [h.id, h]))
    return { wire: [...byId.values()].sort((a, b) => b.id - a.id).slice(0, WIRE_SIZE) }
  }),
  applyHeadline: (headline) => set((s) => (s.wire.some((h) => h.id === headline.id)
    ? s
    : { wire: [headline, ...s.wire].slice(0, WIRE_SIZE) })),
}))
