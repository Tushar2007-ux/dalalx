import { useEffect, useRef, useState } from 'react'
import { changePct, formatPaise, formatQty } from '../lib/format'
import { useMarket } from '../store/market'
import { Odometer } from './Odometer'

/**
 * The selected stock's price as the front-page figure: big, bold, rolling on
 * every change, with the day's move, high, low and volume beside it.
 */
export function PriceHero() {
  const selected = useMarket((s) => s.selected)
  const info = useMarket((s) => s.symbols.find((x) => x.symbol === s.selected))
  const price = useMarket((s) => s.lastPrice[s.selected])
  const tape = useMarket((s) => s.tapes[s.selected])
  const phase = useMarket((s) => s.status[s.selected]?.phase ?? 'OPEN')
  const before = useRef<number | undefined>(undefined)
  const [tone, setTone] = useState<{ dir: 'up' | 'down'; key: number } | null>(null)

  useEffect(() => {
    if (price !== undefined && before.current !== undefined && price !== before.current && selected) {
      setTone({ dir: price > before.current ? 'up' : 'down', key: Date.now() })
    }
    before.current = price
  }, [price, selected])
  useEffect(() => { before.current = undefined }, [selected])

  const shown = price ?? info?.lastPrice
  if (shown === undefined || !info) {
    return <div className="dx-hero dx-hero-wait"><span className="dx-shimmer" style={{ width: 260, height: 46 }} /></div>
  }
  const pct = changePct(shown, info.referencePrice)
  const diff = shown - info.referencePrice
  const prices = (tape ?? []).map((t) => t.price)
  const high = prices.length ? Math.max(...prices) : shown
  const low = prices.length ? Math.min(...prices) : shown
  const volume = (tape ?? []).reduce((a, t) => a + t.qty, 0)

  return (
    <div className="dx-hero" aria-live="off">
      <div className="dx-hero-name">
        <strong>{selected}</strong>
        <span>{info.name.replace(' (simulated)', '')}</span>
      </div>
      <div className="dx-hero-price">
        <Odometer key={tone?.key} value={`₹${formatPaise(shown)}`} tone={tone?.dir} />
      </div>
      <div className={`dx-hero-move dx-num ${pct >= 0 ? 'is-up' : 'is-down'}`}>
        <span>{pct >= 0 ? '▲' : '▼'} {Math.abs(pct).toFixed(2)}%</span>
        <small>{diff >= 0 ? '+' : '−'}₹{formatPaise(Math.abs(diff))} today</small>
      </div>
      <dl className="dx-hero-facts dx-num">
        {phase !== 'OPEN' && <div><dt>Phase</dt><dd className="is-halt">{phase === 'HALTED' ? 'Halted' : 'Auction'}</dd></div>}
        <div><dt>High</dt><dd>₹{formatPaise(high)}</dd></div>
        <div><dt>Low</dt><dd>₹{formatPaise(low)}</dd></div>
        <div><dt>Volume</dt><dd>{formatQty(volume)}</dd></div>
      </dl>
    </div>
  )
}
