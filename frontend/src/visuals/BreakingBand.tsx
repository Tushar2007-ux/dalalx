import { useEffect, useState } from 'react'
import { useMarket } from '../store/market'
import { breakingNow } from './signals'

/**
 * BREAKING: an alert headline takes the strip under the ticker for a few
 * seconds, in red, instead of scrolling past with the rest of the wire.
 * Halts, suspensions and surveillance flags are the only news that earns it.
 */
export function BreakingBand() {
  const wire = useMarket((s) => s.wire)
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])
  const headline = breakingNow(wire, now)
  if (!headline) return null
  return (
    <div className="dx-breaking" role="status" key={headline.id}>
      <span className="dx-breaking-flag">Breaking</span>
      <span className="dx-breaking-text">{headline.text}</span>
      {headline.symbol && <a className="dx-breaking-link" href="#/floor" onClick={() => useMarket.getState().select(headline.symbol!)}>{headline.symbol} →</a>}
    </div>
  )
}
