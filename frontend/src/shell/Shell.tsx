import { useEffect, useRef, type ReactNode } from 'react'
import { Edition, Folio, HeadlineBanner, Masthead, Toast } from '../design'
import { clockTime } from '../lib/format'
import { useMarket, type ToastItem } from '../store/market'
import { ToolsMenu } from './ToolsMenu'
import type { Headline } from '../terminal/headline'
import { ROUTES, type ShellRoute } from './route'
import { TickerStrip } from './TickerStrip'
import './shell.css'

const EDITION_NAMES = { day: 'Day Edition', night: 'Night Edition' } as const

const today = () => new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })

/**
 * The newspaper around every page: edition (Day/Night), strip masthead with
 * page links, connection light and edition switch, the headline banner, the
 * folio at the foot and the toasts. Pages only supply their own columns.
 */
export function Shell({ route, headline, page, section, status, band, full = false, children }: {
  route: ShellRoute
  /** Overrides the masthead's market status (e.g. 'halted' for the selected stock). */
  status?: 'live' | 'halted' | 'auction' | 'closed'
  /** Optional band printed between the headline and the page (halt, auction). */
  band?: ReactNode
  /** The banner headline; the front page has none. */
  headline?: Headline & { time?: string }
  /** The full nameplate (front page) instead of the slim strip. */
  full?: boolean
  page: number
  section: string
  children: ReactNode
}) {
  const edition = useMarket((s) => s.edition)
  const setEdition = useMarket((s) => s.setEdition)
  const connection = useMarket((s) => s.connection)
  const toasts = useMarket((s) => s.toasts)
  const dismiss = useMarket((s) => s.dismissToast)

  const sections = (
    <div className="dx-sections">
      <nav aria-label="Pages" style={{ display: 'flex', gap: '4px 18px', flexWrap: 'wrap' }}>
        {ROUTES.map((r) => (
          <a key={r.route} href={r.hash} className="dx-focus" aria-current={r.route === route ? 'page' : undefined}>{r.label}</a>
        ))}
      </nav>
      <span className="dx-sections-right">
      <ToolsMenu />
      <a href="#/wall" className="dx-focus dx-sections-board" title="Full-screen wall display for the projector (W)">Big Board ⤢</a>
      <span data-testid="connection" style={{ fontSize: 'var(--text-small)', color: connection === 'live' ? 'var(--buy)' : 'var(--ink-muted)' }}>
        {connection === 'live' ? '● Live wire' : connection === 'connecting' ? '○ Connecting…' : '○ Offline'}
      </span>
      <button type="button" className="dx-focus dx-mode" onClick={() => setEdition(edition === 'day' ? 'night' : 'day')}
        title="Switch between the day and night editions (N)">{edition === 'day' ? 'Night edition →' : 'Day edition →'}</button>
      </span>
    </div>
  )

  return (
    <Edition edition={edition} style={{ minHeight: '100vh', padding: '0 24px 18px', boxSizing: 'border-box', display: 'flex', flexDirection: 'column' }}>
      {full
        ? <>
            <Masthead date={today()} editionName={EDITION_NAMES[edition]} leftMeta="Vol. I · A simulated stock exchange"
              rightMeta="Free · paper money only" status={connection === 'offline' ? 'closed' : status ?? 'live'} style={{ marginBottom: 0 }} />
            {sections}
          </>
        : <>
            <Masthead strip date={today()} editionName={EDITION_NAMES[edition]}
              status={connection === 'offline' ? 'closed' : status ?? 'live'} />
            {sections}
          </>}
      <TickerStrip />
      {headline && <HeadlineBanner {...headline} time={headline.time ?? clockTime(Date.now()).slice(0, 8)} />}
      {band}
      {children}
      <Folio page={page} section={section} right={<span>Prices in ₹ · simulated exchange · paper money only</span>} style={{ marginTop: 'var(--space-4)' }} />
      <div className="dx-toasts" aria-live="polite">
        {toasts.map((t) => <AutoToast key={t.id} toast={t} onClose={() => dismiss(t.id)} />)}
      </div>
    </Edition>
  )
}

/**
 * Toasts stack up from the bottom right: the top of the page carries the
 * headline, the halt countdown and the auction figures, and a notice must
 * never sit on top of those. They leave by themselves after a few seconds.
 */
const TOAST_MS = 6000

function AutoToast({ toast, onClose }: { toast: ToastItem; onClose: () => void }) {
  const close = useRef(onClose)
  // The parent passes a new function on every render; keep the latest in a ref so the timer never restarts.
  useEffect(() => { close.current = onClose }, [onClose])
  useEffect(() => {
    const timer = setTimeout(() => close.current(), TOAST_MS)
    return () => clearTimeout(timer)
  }, [])
  return <Toast kind={toast.kind} title={toast.title} detail={toast.detail} time={toast.time} onClose={onClose} />
}
