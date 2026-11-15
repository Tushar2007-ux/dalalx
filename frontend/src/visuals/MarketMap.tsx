import { changePct, formatPaise } from '../lib/format'
import { useMarket } from '../store/market'
import { layoutTiles, moveInk } from './signals'
import { Spark } from './Spark'

const W = 100
const H = 62

/**
 * The market as a map: one tile per stock, sized by how much it has traded
 * and inked by how far it has moved — green for up, red for down, darker for
 * further. One glance says where the action is and which way it is going.
 */
export function MarketMap() {
  const symbols = useMarket((s) => s.symbols)
  const lastPrice = useMarket((s) => s.lastPrice)
  const tapes = useMarket((s) => s.tapes)
  const trail = useMarket((s) => s.trail)
  const status = useMarket((s) => s.status)
  const select = useMarket((s) => s.select)
  if (!symbols.length) return null

  // Weight by traded value on the tape; a floor keeps a quiet stock on the map.
  const turnover = (symbol: string) => (tapes[symbol] ?? []).reduce((sum, t) => sum + t.price * t.qty, 0)
  const most = Math.max(1, ...symbols.map((x) => turnover(x.symbol)))
  const tiles = layoutTiles(symbols.map((x) => ({ key: x.symbol, weight: turnover(x.symbol) + most * 0.35 })), W, H)

  return (
    <div className="dx-map" role="list" aria-label="Market map">
      {tiles.map((t) => {
        const info = symbols.find((x) => x.symbol === t.key)!
        const price = lastPrice[t.key] ?? info.referencePrice
        const pct = changePct(price, info.referencePrice)
        const halted = (status[t.key]?.phase ?? 'OPEN') !== 'OPEN'
        return (
          <a key={t.key} role="listitem" href="#/floor" onClick={() => select(t.key)}
            className={`dx-map-tile dx-focus ${halted ? 'is-halted' : pct > 0 ? 'is-up' : pct < 0 ? 'is-down' : ''}`}
            style={{ left: `${t.x}%`, top: `${(t.y / H) * 100}%`, width: `${t.w}%`, height: `${(t.h / H) * 100}%`,
              ['--ink-share' as string]: (0.1 + moveInk(pct) * 0.62).toFixed(2) }}>
            <span className="dx-map-symbol">{t.key}</span>
            <span className="dx-map-pct dx-num">{halted ? 'HALTED' : `${pct > 0 ? '▲' : pct < 0 ? '▼' : '·'} ${Math.abs(pct).toFixed(2)}%`}</span>
            <span className="dx-map-price dx-num">₹{formatPaise(price)}</span>
            <Spark values={trail[t.key]} width={64} height={18} />
          </a>
        )
      })}
    </div>
  )
}
