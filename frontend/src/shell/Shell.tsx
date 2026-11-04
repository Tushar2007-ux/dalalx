import { useEffect, useRef, type ReactNode } from 'react'
import { Edition, Folio, HeadlineBanner, Masthead, Toast } from '../design'
import { clockTime } from '../lib/format'
import { useMarket, type ToastItem } from '../store/market'
import type { Headline } from '../terminal/headline'
import { ROUTES, type Route } from './route'

const EDITION_NAMES = { day: 'Day Edition', night: 'Night Edition' } as const

const today = () => new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })

const navLink = (active: boolean) => ({
  fontFamily: 'var(--font-ui)', fontSize: 12, fontWeight: active ? 700 : 500, color: 'var(--ink)',
  textDecoration: active ? 'none' : 'underline', textUnderlineOffset: 3,
})

/**
 * The newspaper around every page: edition (Day/Night), strip masthead with
 * page links, connection light and edition switch, the headline banner, the
 * folio at the foot and the toasts. Pages only supply their own columns.
 */
export function Shell({ route, headline, page, section, status, band, children }: {
  route: Route
  /** Overrides the masthead's market status (e.g. 'halted' for the selected stock). */
  status?: 'live' | 'halted' | 'auction' | 'closed'
  /** Optional band printed between the headline and the page (halt, auction). */
  band?: ReactNode
  headline: Headline & { time?: string }
  page: number
  section: string
  children: ReactNode
}) {
  const edition = useMarket((s) => s.edition)
  const setEdition = useMarket((s) => s.setEdition)
  const connection = useMarket((s) => s.connection)
  const toasts = useMarket((s) => s.toasts)
  const dismiss = useMarket((s) => s.dismissToast)

  const right = (
    <span style={{ display: 'flex', gap: 14, alignItems: 'center', flexWrap: 'wrap' }}>
      <nav aria-label="Pages" style={{ display: 'flex', gap: 12 }}>
        {ROUTES.map((r) => (
          <a key={r.route} href={r.hash} className="dx-focus" aria-current={r.route === route ? 'page' : undefined}
            style={navLink(r.route === route)}>{r.label}</a>
        ))}
      </nav>
      <span data-testid="connection" style={{ fontSize: 12, color: connection === 'live' ? 'var(--buy)' : 'var(--ink-muted)' }}>
        {connection === 'live' ? '● Live wire' : connection === 'connecting' ? '○ Connecting…' : '○ Offline'}
      </span>
      <button type="button" className="dx-focus" onClick={() => setEdition(edition === 'day' ? 'night' : 'day')}
        style={{ appearance: 'none', background: 'none', border: '1px solid var(--rule-strong)', color: 'var(--ink)', fontFamily: 'var(--font-ui)', fontSize: 12, fontWeight: 600, padding: '3px 9px', cursor: 'pointer' }}>
        {edition === 'day' ? 'Night edition →' : 'Day edition →'}
      </button>
    </span>
  )

  return (
    <Edition edition={edition} style={{ minHeight: '100vh', padding: '0 24px 18px', boxSizing: 'border-box', display: 'flex', flexDirection: 'column' }}>
      <Masthead strip date={today()} editionName={EDITION_NAMES[edition]}
        status={connection === 'offline' ? 'closed' : status ?? 'live'} right={right} />
      <HeadlineBanner {...headline} time={headline.time ?? clockTime(Date.now()).slice(0, 8)} />
      {band}
      {children}
      <Folio page={page} section={section} right={<span>Prices in ₹ · simulated exchange · paper money only</span>} style={{ marginTop: 18 }} />
      <div aria-live="polite" style={{ position: 'fixed', top: 72, right: 16, display: 'grid', gap: 8, zIndex: 40 }}>
        {toasts.map((t) => <AutoToast key={t.id} toast={t} onClose={() => dismiss(t.id)} />)}
      </div>
    </Edition>
  )
}

/** Toasts sit below the masthead (so they never cover its buttons) and leave by themselves after a few seconds. */
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
