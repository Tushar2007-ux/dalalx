import { useEffect } from 'react'
import { Monogram, Portrait, RubberStamp } from '../design'
import { botKey } from '../lib/market'
import { monogramOf } from '../lib/monogram'
import { useMarket, type Moment } from '../store/market'

/** How long a big moment stays up. */
const SHOW_MS = 3600

type BotKey = 'munim' | 'momentum' | 'panic' | 'whale' | 'spoofer' | 'inspector'

/**
 * The newspaper's "special edition" moments, printed over whatever page is
 * open: EXTRA EDITION when a circuit breaker trips, the bell when an auction
 * reopens a stock, and CAUGHT when the Inspector suspends a trader. A page
 * drops in, a stamp slams, and it clears itself after a few seconds (or on a
 * click or Escape). People who prefer reduced motion get the same page,
 * without the movement.
 */
export function MomentOverlay() {
  const moment = useMarket((s) => s.moment)
  const clear = useMarket((s) => s.clearMoment)

  useEffect(() => {
    if (!moment) return
    const timer = setTimeout(clear, SHOW_MS)
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && clear()
    window.addEventListener('keydown', onKey)
    return () => { clearTimeout(timer); window.removeEventListener('keydown', onKey) }
  }, [moment, clear])

  if (!moment) return null
  return (
    <div className="dx-moment" role="alert" onClick={clear} key={moment.id}>
      <div className={`dx-moment-sheet dx-moment-${moment.kind}`}>
        <div className="dx-moment-kicker">{moment.kicker}</div>
        <Art moment={moment} />
        <h2 className="dx-moment-headline">{moment.headline}</h2>
        {moment.deck && <p className="dx-moment-deck">{moment.deck}</p>}
        <div className="dx-moment-hint">Click anywhere to carry on reading</div>
      </div>
    </div>
  )
}

function Art({ moment }: { moment: Moment }) {
  if (moment.kind === 'extra') {
    return <div className="dx-moment-art"><RubberStamp kind="stop-press" size="lg" angle={-8} sub="Circuit breaker" animate slamKey={moment.id} /></div>
  }
  if (moment.kind === 'bell') {
    return (
      <div className="dx-moment-art">
        <svg className="dx-moment-bell" width="96" height="96" viewBox="0 0 96 96" aria-hidden="true">
          <g fill="none" stroke="var(--ink)" strokeWidth="3" strokeLinejoin="round">
            <path d="M48 12 c-18 0 -26 14 -26 30 v14 l-8 12 h68 l-8 -12 v-14 c0 -16 -8 -30 -26 -30 z" fill="var(--surface)" />
            <path d="M40 72 a8 8 0 0 0 16 0" />
            <line x1="48" y1="4" x2="48" y2="12" />
            <path d="M30 40 q2 -12 12 -16" strokeWidth="1.5" />
          </g>
        </svg>
      </div>
    )
  }
  const key = moment.traderId ? botKey(moment.traderId) : undefined
  return (
    <div className="dx-moment-art dx-moment-caught-art">
      <Portrait bot="inspector" size={120} />
      <span className="dx-moment-vs">→</span>
      {key && key !== 'you'
        ? <Portrait bot={key as BotKey} size={120} />
        : <Monogram bot={monogramOf(moment.traderId ?? '??')} size={80} />}
      <div className="dx-moment-caught-stamp"><RubberStamp text="Suspended" size="lg" angle={-10} animate slamKey={moment.id} backed /></div>
    </div>
  )
}
