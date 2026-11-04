import { useEffect } from 'react'
import { api } from '../lib/api'
import { connectStream, type MarketStream } from '../lib/stream'
import { useMarket } from '../store/market'

const RETRY_MS = 3000

/**
 * Boots the terminal's data: the symbol list over REST, then live updates over
 * STOMP for every listed stock, plus a REST snapshot of the selected stock's
 * book, tape and candles whenever the selection or chart interval changes.
 *
 * If the server is down the terminal shows the "market closed" edition and
 * keeps retrying quietly in the background.
 */
export function useMarketData(): void {
  const selected = useMarket((s) => s.selected)
  const interval = useMarket((s) => s.interval)
  const connection = useMarket((s) => s.connection)
  const symbolCount = useMarket((s) => s.symbols.length)

  // 1. Symbols, then the stream.
  useEffect(() => {
    const store = useMarket.getState()
    let stream: MarketStream | null = null
    let retry: ReturnType<typeof setTimeout> | undefined
    let stopped = false

    const boot = async () => {
      try {
        const symbols = await api.symbols()
        if (stopped) return
        store.setSymbols(symbols)
        stream = connectStream({
          onBook: (b) => useMarket.getState().applyBook(b),
          onTrades: (sym, t) => useMarket.getState().applyTrades(sym, t),
          onActivity: (a) => useMarket.getState().applyActivity(a),
          onConnection: (up) => useMarket.getState().setConnection(up ? 'live' : 'offline'),
        })
        symbols.forEach((s) => stream?.watch(s.symbol))
      } catch {
        if (stopped) return
        store.setConnection('offline')
        retry = setTimeout(boot, RETRY_MS)
      }
    }
    void boot()
    return () => {
      stopped = true
      clearTimeout(retry)
      stream?.close()
    }
  }, [])

  // 2. Snapshots for the selected stock (re-fetched after a reconnect, too).
  useEffect(() => {
    if (!symbolCount || connection === 'offline') return
    let stale = false
    const store = useMarket.getState()
    api.book(selected).then((b) => !stale && store.applyBook(b)).catch(() => {})
    api.trades(selected, 40).then((t) => !stale && store.setTape(selected, t)).catch(() => {})
    api.candles(selected, interval).then((c) => !stale && store.setCandles(selected, interval, c)).catch(() => {})
    return () => { stale = true }
  }, [selected, interval, connection, symbolCount])
}
