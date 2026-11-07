import { useEffect, useState } from 'react'
import { useMarket } from '../store/market'
import './intro.css'

/** The whole intro, start to fold-open. */
export const INTRO_MS = 4300
const SEEN_KEY = 'dalalx-intro-seen'

/** Shown once per browser session, never in tests, the demo or on phones joining from the audience. */
export function shouldPlayIntro(hash: string, seen: boolean, reducedMotion: boolean): boolean {
  if (seen || reducedMotion) return false
  return !['#/join', '#/demo', '#/wall'].includes(hash)
}

function alreadySeen(): boolean {
  try {
    return sessionStorage.getItem(SEEN_KEY) === '1'
  } catch {
    return false
  }
}

/**
 * "The presses roll": a four-second opening the first time DalalX is opened.
 *
 * Rollers spin, the masthead is stamped onto a blank page, columns of type
 * roll in, the day's headline slams down, and then the page folds open onto
 * the live site. Click, or press any key, to skip. Played once per browser
 * session, and never for people who prefer reduced motion.
 */
export function PressIntro() {
  const edition = useMarket((s) => s.edition)
  const [show, setShow] = useState(() => shouldPlayIntro(location.hash, alreadySeen(),
    typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches))

  useEffect(() => {
    if (!show) return
    try { sessionStorage.setItem(SEEN_KEY, '1') } catch { /* plays again next time */ }
    const done = () => setShow(false)
    const timer = setTimeout(done, INTRO_MS)
    window.addEventListener('keydown', done)
    return () => { clearTimeout(timer); window.removeEventListener('keydown', done) }
  }, [show])

  if (!show) return null
  const date = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
  return (
    <div className="dx-intro" data-edition={edition} onClick={() => setShow(false)} role="presentation">
      <div className="dx-intro-rollers" aria-hidden="true"><i /><i /><i /></div>
      <div className="dx-intro-page">
        <div className="dx-intro-half dx-intro-top">
          <div className="dx-intro-meta"><span>Vol. I · A simulated stock exchange</span><span>{date}</span></div>
          <div className="dx-intro-mast">DalalX</div>
          <div className="dx-intro-rule" />
          <div className="dx-intro-headline">The Market Is Open</div>
        </div>
        <div className="dx-intro-half dx-intro-bottom">
          <div className="dx-intro-cols" aria-hidden="true">
            {Array.from({ length: 4 }, (_, c) => (
              <div key={c} style={{ animationDelay: `${1500 + c * 140}ms` }}>
                {Array.from({ length: 9 }, (_, i) => <span key={i} style={{ width: `${62 + ((c * 13 + i * 29) % 38)}%` }} />)}
              </div>
            ))}
          </div>
          <div className="dx-intro-deck">Five stocks. Five threads. One fair price.</div>
        </div>
      </div>
      <div className="dx-intro-skip">Click or press any key to skip</div>
    </div>
  )
}
