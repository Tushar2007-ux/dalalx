import { useMemo } from 'react'
import { CandleChart, PriceCell, RuledPanel, SectionLabel, TradeTapeRow } from '../design'
import { changePct, clockTime, hhmm, toRupees } from '../lib/format'
import { botKey, ME } from '../lib/market'
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
  const interval = useMarket((s) => s.interval)
  const setInterval = useMarket((s) => s.setInterval)
  const info = useMarket((s) => s.symbols.find((x) => x.symbol === s.selected))
  const price = useMarket((s) => s.lastPrice[s.selected])
  const chart = useMarket((s) => s.chart)
  const tape = useMarket((s) => s.tapes[s.selected])

  const candles = useMemo(
    () => (chart?.key === chartKey(selected, interval) ? chart.candles.map((c) => toChartCandle(c, interval)) : []),
    [chart, selected, interval],
  )
  const reference = info?.referencePrice
  const shown = price ?? reference

  return (
    <RuledPanel label={selected} meta={info?.name ?? ''} columnRule="left"
      style={{ minWidth: 0, display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, margin: '4px 0 6px', flexWrap: 'wrap' }}>
        {shown !== undefined && <PriceCell value={toRupees(shown)} size="xl" prefix="₹" align="left" />}
        {shown !== undefined && reference !== undefined && (
          <PriceCell value={toRupees(shown)} change={toRupees(shown - reference)}
            changePct={changePct(shown, reference)} changeOnly size={20} align="left" />
        )}
      </div>
      <div style={{ flex: 1, minHeight: 320, display: 'flex' }}>
        {candles.length
          ? <CandleChart candles={candles} prevClose={interval === '1D' || reference === undefined ? undefined : toRupees(reference)}
              interval={interval} intervals={INTERVALS} onInterval={(iv) => setInterval(iv as Interval)} minHeight={320} />
          : <EmptyChart interval={interval} onInterval={setInterval} />}
      </div>
      <div style={{ marginTop: 12 }}>
        <SectionLabel count={tape?.length ?? 0} meta="Newest first">Trade Tape</SectionLabel>
        {(tape ?? []).slice(0, 8).map((t, i) => (
          <TradeTapeRow key={t.id} time={clockTime(t.timestamp)} symbol={t.symbol}
            side={t.aggressorSide === 'BUY' ? 'buy' : 'sell'} price={toRupees(t.price)} qty={t.qty}
            buyer={t.buyerId} seller={t.sellerId} buyerBot={botKey(t.buyerId)} sellerBot={botKey(t.sellerId)}
            mine={t.buyerId === ME || t.sellerId === ME} fresh={i === 0} />
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
      <div style={{ position: 'absolute', top: 6, right: 8, display: 'flex', gap: 10 }}>
        {INTERVALS.map((iv) => (
          <button key={iv} type="button" className="dx-focus" onClick={() => onInterval(iv)}
            style={{ all: 'unset', cursor: 'pointer', fontSize: 12, fontWeight: iv === interval ? 700 : 400,
              textDecoration: iv === interval ? 'none' : 'underline' }}>{iv}</button>
        ))}
      </div>
      <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', color: 'var(--ink-muted)' }}>
        The pen is waiting for the first trade.
      </span>
    </div>
  )
}
