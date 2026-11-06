import { changePct, formatPaise } from '../lib/format'
import { useMarket } from '../store/market'

/**
 * The scrolling price strip under the section bar, like a broadcaster's
 * ticker. The list is printed twice and slid left by half its width, so the
 * loop is seamless. Each price is keyed by its value, so a change re-mounts
 * it and replays the ink flash (green up, red down). Hovering pauses it, and
 * it stands still for people who prefer reduced motion.
 */
export function TickerStrip() {
  const symbols = useMarket((s) => s.symbols)
  const lastPrice = useMarket((s) => s.lastPrice)
  const status = useMarket((s) => s.status)
  const before = useMarket((s) => s.tickerPrev)
  if (!symbols.length) return null

  const items = symbols.map((x) => {
    const price = lastPrice[x.symbol] ?? x.referencePrice
    const pct = changePct(price, x.referencePrice)
    const phase = status[x.symbol]?.phase ?? 'OPEN'
    const prev = before[x.symbol]
    const flash = prev === undefined || prev === price ? '' : price > prev ? 'dx-anim-ink-up' : 'dx-anim-ink-down'
    return (
      <span key={x.symbol} className="dx-ticker-item">
        <a href="#/floor" onClick={() => useMarket.getState().select(x.symbol)}>{x.symbol}</a>
        <span key={price} className={`dx-num ${flash}`}>{formatPaise(price)}</span>
        <span className="dx-num" style={{ color: pct > 0 ? 'var(--buy)' : pct < 0 ? 'var(--sell)' : 'var(--ink-muted)' }}>
          {pct > 0 ? '▲' : pct < 0 ? '▼' : '·'} {Math.abs(pct).toFixed(2)}%
        </span>
        {phase !== 'OPEN' && <span className="dx-ticker-badge">{phase === 'HALTED' ? 'Halted' : 'Auction'}</span>}
      </span>
    )
  })

  return (
    <div className="dx-ticker" aria-label="Live prices">
      <div className="dx-ticker-track">
        <div className="dx-ticker-run">{items}</div>
        <div className="dx-ticker-run" aria-hidden="true">{items}</div>
      </div>
    </div>
  )
}
