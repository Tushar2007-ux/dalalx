import { Edition, Folio, HeadlineBanner, Masthead, StockTabs, Toast } from '../design'
import { changePct, clockTime } from '../lib/format'
import { useEffect, useRef } from 'react'
import { useMarket, type ToastItem } from '../store/market'
import { BookPanel } from './BookPanel'
import { ChartPanel } from './ChartPanel'
import { priceHeadline } from './headline'
import { MarketsPanel } from './MarketsPanel'
import { TicketPanel } from './TicketPanel'
import { useMarketData } from './useMarketData'

const EDITION_NAMES = { day: 'Day Edition', night: 'Night Edition' } as const

const today = () => new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })

/**
 * The trading floor: masthead, headline, stock tabs, then four newspaper
 * columns (markets · chart & tape · order book · ticket). Layout follows
 * design/export/ui_kits/trading-floor-v2.
 */
export function Terminal() {
  useMarketData()
  const edition = useMarket((s) => s.edition)
  const setEdition = useMarket((s) => s.setEdition)
  const connection = useMarket((s) => s.connection)
  const symbols = useMarket((s) => s.symbols)
  const selected = useMarket((s) => s.selected)
  const select = useMarket((s) => s.select)
  const lastPrice = useMarket((s) => s.lastPrice)
  const toasts = useMarket((s) => s.toasts)
  const dismiss = useMarket((s) => s.dismissToast)

  const info = symbols.find((x) => x.symbol === selected)
  const headline = priceHeadline(selected, lastPrice[selected], info?.referencePrice, connection)
  const tabs = symbols.map((x) => ({
    symbol: x.symbol, name: x.name, change: changePct(lastPrice[x.symbol] ?? x.referencePrice, x.referencePrice),
  }))

  const editionButton = (
    <span style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
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
        status={connection === 'offline' ? 'closed' : 'live'} right={editionButton} />
      <HeadlineBanner {...headline} time={clockTime(Date.now()).slice(0, 8)} />
      <div style={{ marginTop: 12 }}>
        <StockTabs tabs={tabs} value={selected} onChange={select} />
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'stretch', rowGap: 24, marginTop: 14, flex: 1,
        filter: connection === 'offline' ? 'grayscale(0.8) sepia(0.2)' : 'none', transition: 'filter 900ms var(--ease-out)' }}>
        <MarketsPanel />
        <ChartPanel />
        <BookPanel />
        <TicketPanel />
      </div>
      <Folio page={1} section="Markets" right={<span>Prices in ₹ · simulated exchange</span>} style={{ marginTop: 18 }} />
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
