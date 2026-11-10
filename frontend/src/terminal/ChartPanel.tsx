import { useEffect, useMemo, useRef } from 'react'
import { CandleChart, RuledPanel, SectionLabel, TradeTapeRow } from '../design'
import { clockTime, hhmm, toRupees } from '../lib/format'
import { addTradeToCandles, botKey, ME } from '../lib/market'
import { isBlockTrade } from './tape'
import { INTERVALS, type Candle, type Interval } from '../lib/types'
import { chartKey, useMarket } from '../store/market'

/** Candle from the server (paise, epoch millis) → the shape the chart component draws (rupees, label). */
function toChartCandle(c: Candle, interval: Interval) {
  const d = new Date(c.startMillis)
  const t = interval === '1D' ? d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) : hhmm(d)
  return { t, o: toRupees(c.open), h: toRupees(c.high), l: toRupees(c.low), c: toRupees(c.close), v: c.volume }
}

/** Centre column: big last price, the candlestick chart and the trade tape for the selected stock. */
export function ChartPanel() {
  const selected = useMarket((s) => s.selected)
  const names = useMarket((s) => s.names)
  // Bots keep their ids (the tape shows their stamp); audience guests show the name they joined with.
  const nameOf = (id: string) => (botKey(id) ? id : names[id] ?? id)
  const interval = useMarket((s) => s.interval)
  const setInterval = useMarket((s) => s.setInterval)
  const info = useMarket((s) => s.symbols.find((x) => x.symbol === s.selected))
  const price = useMarket((s) => s.lastPrice[s.selected])
  const chart = useMarket((s) => s.chart)
  const tape = useMarket((s) => s.tapes[s.selected])

  const candles = useMemo(() => {
    const fromServer = chart?.key === chartKey(selected, interval) ? chart.candles : []
    // Before the server's candles arrive, draw from the trades already on the tape, so the chart is never blank.
    const source = fromServer.length ? fromServer
      : [...(tape ?? [])].reverse().reduce<Candle[]>((acc, t) => addTradeToCandles(acc, t, interval), [])
    return source.map((c) => toChartCandle(c, interval))
  }, [chart, selected, interval, tape])
  const reference = info?.referencePrice
  void price

  // Only rows that weren't on the tape last time slide in (none on first paint or after switching stock).
  const shownBefore = useRef<{ symbol: string; ids: Set<number> } | null>(null)
  const before = shownBefore.current?.symbol === selected ? shownBefore.current.ids : null
  const fresh = new Set(before ? (tape ?? []).filter((t) => !before.has(t.id)).map((t) => t.id) : [])
  useEffect(() => {
    shownBefore.current = { symbol: selected, ids: new Set((tape ?? []).map((t) => t.id)) }
  }, [tape, selected])

  return (
    <RuledPanel label={selected} meta={info?.name ?? ''} columnRule="left"
      style={{ minWidth: 0, display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, minHeight: 320, display: 'flex' }}>
        {candles.length
          ? <CandleChart candles={candles} prevClose={interval === '1D' || reference === undefined ? undefined : toRupees(reference)}
              interval={interval} intervals={INTERVALS} onInterval={(iv) => setInterval(iv as Interval)} minHeight={320} />
          : <EmptyChart interval={interval} onInterval={setInterval} />}
      </div>
      <div style={{ marginTop: 'var(--space-3)' }}>
        <SectionLabel count={tape?.length ?? 0} meta="Newest first">Trade Tape</SectionLabel>
        {(tape ?? []).slice(0, 8).map((t, i) => (
          <div key={t.id} className={`dx-tape-row${fresh.has(t.id) ? ' is-new' : ''}${isBlockTrade(t) ? ' is-block' : ''}`}>
          <TradeTapeRow time={clockTime(t.timestamp)} symbol={t.symbol}
            side={t.aggressorSide === 'BUY' ? 'buy' : 'sell'} price={toRupees(t.price)} qty={t.qty}
            buyer={nameOf(t.buyerId)} seller={nameOf(t.sellerId)} buyerBot={botKey(t.buyerId)} sellerBot={botKey(t.sellerId)}
            mine={t.buyerId === ME || t.sellerId === ME} fresh={i === 0} />
          </div>
        ))}
        {!tape?.length && <p style={{ margin: '8px 0', fontFamily: 'var(--font-display)', fontStyle: 'italic', color: 'var(--ink-muted)' }}>
          No trades printed yet. Place a crossing order to make the first one.
        </p>}
      </div>
    </RuledPanel>
  )
}

/** Before the first trade there is nothing to draw: keep the interval tabs and say so. */
function EmptyChart({ interval, onInterval }: { interval: Interval; onInterval: (iv: Interval) => void }) {
  return (
    <div style={{ flex: 1, display: 'grid', placeItems: 'center', border: '1px solid var(--rule)', position: 'relative' }}>
      <div style={{ position: 'absolute', top: 6, right: 8, display: 'flex', gap: 'var(--space-2)' }}>
        {INTERVALS.map((iv) => (
          <button key={iv} type="button" className="dx-focus" onClick={() => onInterval(iv)}
            style={{ all: 'unset', cursor: 'pointer', fontSize: 'var(--text-small)', fontWeight: iv === interval ? 700 : 400,
              textDecoration: iv === interval ? 'none' : 'underline' }}>{iv}</button>
        ))}
      </div>
      <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', color: 'var(--ink-muted)' }}>
        The pen is waiting for the first trade.
      </span>
    </div>
  )
}
