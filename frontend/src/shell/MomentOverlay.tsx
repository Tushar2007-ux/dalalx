import { useEffect } from 'react'
import { Monogram, Portrait, RubberStamp } from '../design'
import { botKey } from '../lib/market'
import { monogramOf } from '../lib/monogram'
import { useMarket, type Moment } from '../store/market'

/** How long a big moment stays up (the spin-in takes the first 0.9 s). */
export const SHOW_MS = 4600

const KICKERS: Record<Moment['kind'], string> = { extra: 'Extra · Extra', bell: 'Market Special', caught: 'Investigation' }

/** Today's date as a newspaper prints it. */
const printedDate = () => new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })

type BotKey = 'munim' | 'momentum' | 'panic' | 'whale' | 'spoofer' | 'inspector'

/**
 * The newspaper's "special edition" moments, printed over whatever page is
 * open: EXTRA when a circuit breaker trips, the bell when an auction reopens
 * a stock, and CAUGHT when the Inspector suspends a trader.
 *
 * Like the old film trick, a front page spins in from the middle of the
 * screen, slams flat with an ink splash and a jolt, then a stamp lands on it.
 * It clears itself after a few seconds (or on a click or Escape). People who
 * prefer reduced motion get the same front page, without the movement.
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
    <div className={`dx-moment dx-moment-${moment.kind}`} role="alert" onClick={clear} key={moment.id}>
      <InkSplash />
      <div className="dx-moment-spin">
        <article className="dx-moment-sheet">
          <header className="dx-moment-nameplate">
            <span>{KICKERS[moment.kind]}</span>
            <strong>The DalalX Times</strong>
            <span>{printedDate()}</span>
          </header>
          <div className="dx-moment-kicker">{moment.kicker}</div>
          <h2 className="dx-moment-headline">{moment.headline}</h2>
          <div className="dx-moment-body">
            <div className="dx-moment-photo"><Art moment={moment} /></div>
            <div className="dx-moment-copy">
              {moment.deck && <p className="dx-moment-deck">{moment.deck}</p>}
              <div className="dx-moment-columns" aria-hidden="true">
                {Array.from({ length: 9 }, (_, i) => <span key={i} style={{ width: `${70 + ((i * 37) % 30)}%` }} />)}
              </div>
            </div>
          </div>
          <div className="dx-moment-hint">Click anywhere to carry on reading</div>
        </article>
      </div>
    </div>
  )
}

/** Blots of printer's ink that burst behind the page as it lands. */
function InkSplash() {
  return (
    <svg className="dx-moment-splash" viewBox="-100 -100 200 200" aria-hidden="true">
      <g fill="currentColor">
        <path d="M-58,-20 C-70,-55 -30,-78 0,-66 C28,-90 70,-60 60,-24 C92,-6 80,40 46,44 C40,80 -6,84 -22,58 C-60,72 -84,32 -62,8 C-90,-6 -80,-22 -58,-20 Z" />
        <circle cx="-82" cy="-52" r="7" /><circle cx="84" cy="-48" r="5" /><circle cx="78" cy="62" r="8" />
        <circle cx="-74" cy="64" r="5" /><circle cx="6" cy="-92" r="4" /><circle cx="-94" cy="10" r="3.5" />
        <circle cx="96" cy="14" r="3" /><circle cx="22" cy="90" r="5" />
      </g>
    </svg>
  )
}

function Art({ moment }: { moment: Moment }) {
  if (moment.kind === 'extra') {
    return <div className="dx-moment-art dx-moment-stamp-late"><RubberStamp kind="stop-press" size="lg" angle={-8} sub="Circuit breaker" animate slamKey={moment.id} /></div>
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
      <div className="dx-moment-caught-stamp dx-moment-stamp-late"><RubberStamp text="Suspended" size="lg" angle={-10} animate slamKey={moment.id} backed /></div>
    </div>
  )
}
