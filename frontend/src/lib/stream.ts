import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import type { BookSnapshot, Case, MarketStatusChanged, OrderActivity, Trade, WireHeadline } from './types'

/**
 * Live market data over STOMP (a small messaging protocol on top of WebSocket).
 *
 * The server pushes at most every 100 ms per topic. We still don't touch React
 * state for every message: messages are collected in plain arrays and applied
 * once per animation frame (requestAnimationFrame), so the screen redraws at
 * most ~60 times a second however busy the market is.
 */
export interface StreamHandlers {
  onBook: (book: BookSnapshot) => void
  onTrades: (symbol: string, trades: Trade[]) => void
  onActivity: (activity: OrderActivity[]) => void
  onHeadline: (headline: WireHeadline) => void
  onStatus: (status: MarketStatusChanged) => void
  onCase: (c: Case) => void
  onConnection: (connected: boolean) => void
}

export interface MarketStream {
  /** Subscribe to one symbol's book and trades; returns an unsubscribe function. */
  watch: (symbol: string) => () => void
  close: () => void
}

function brokerUrl(): string {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${location.host}/ws`
}

export function connectStream(handlers: StreamHandlers): MarketStream {
  const watched = new Map<string, StompSubscription[]>()
  const books = new Map<string, BookSnapshot>()   // only the newest book per symbol matters
  const trades: { symbol: string; batch: Trade[] }[] = []
  const activity: OrderActivity[] = []
  let frame = 0

  const flush = () => {
    frame = 0
    books.forEach((book) => handlers.onBook(book))
    books.clear()
    trades.splice(0).forEach(({ symbol, batch }) => handlers.onTrades(symbol, batch))
    if (activity.length) handlers.onActivity(activity.splice(0))
  }
  const schedule = () => {
    if (!frame) frame = requestAnimationFrame(flush)
  }

  const client = new Client({
    brokerURL: brokerUrl(),
    reconnectDelay: 2000,
    onConnect: () => {
      handlers.onConnection(true)
      client.subscribe('/topic/orders', (m: IMessage) => {
        activity.push(...(JSON.parse(m.body) as OrderActivity[]))
        schedule()
      })
      // Headlines are rare, so they skip the frame buffer.
      client.subscribe('/topic/wire', (m: IMessage) => handlers.onHeadline(JSON.parse(m.body) as WireHeadline))
      client.subscribe('/topic/surveillance', (m: IMessage) => handlers.onCase(JSON.parse(m.body) as Case))
      client.subscribe('/topic/status', (m: IMessage) => handlers.onStatus(JSON.parse(m.body) as MarketStatusChanged))
      ;[...watched.keys()].forEach(subscribeSymbol)
    },
    onWebSocketClose: () => handlers.onConnection(false),
  })

  function subscribeSymbol(symbol: string) {
    if (!client.connected) {
      watched.set(symbol, [])
      return
    }
    watched.set(symbol, [
      client.subscribe(`/topic/book.${symbol}`, (m) => {
        books.set(symbol, JSON.parse(m.body) as BookSnapshot)
        schedule()
      }),
      client.subscribe(`/topic/trades.${symbol}`, (m) => {
        trades.push({ symbol, batch: JSON.parse(m.body) as Trade[] })
        schedule()
      }),
    ])
  }

  client.activate()
  return {
    watch(symbol) {
      subscribeSymbol(symbol)
      return () => {
        watched.get(symbol)?.forEach((s) => s.unsubscribe())
        watched.delete(symbol)
      }
    },
    close() {
      if (frame) cancelAnimationFrame(frame)
      void client.deactivate()
    },
  }
}
