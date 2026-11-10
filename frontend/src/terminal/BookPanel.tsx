import { useEffect, useRef, useState } from 'react'
import { OrderBookRow, RuledPanel } from '../design'
import { formatPaise, formatQty, toRupees } from '../lib/format'
import { botKey, isWorking } from '../lib/market'
import type { Level } from '../lib/types'
import { useMarket } from '../store/market'
import { sideChanges } from './bookChanges'
import { launchTicket } from './flight'

/** How long an eaten level lingers, crossed out, before it folds away. */
const GONE_MS = 650

interface Ghost { side: 'bid' | 'ask'; level: Level; until: number }

const header = { fontFamily: 'var(--font-mono)', fontSize: 'var(--text-micro)', color: 'var(--ink-muted)' }

/**
 * Right-centre column: ten levels each side. Asks are printed highest first so
 * the two best prices meet in the middle, with the spread between them.
 * Clicking a level copies its price into the order ticket.
 *
 * Matching is visible: when trades eat into a level its bar flashes and
 * shrinks, and a level eaten completely is crossed out and folds away, so you
 * can watch price-time priority work its way through the book.
 */
export function BookPanel() {
  const selected = useMarket((s) => s.selected)
  const book = useMarket((s) => s.books[s.selected])
  const last = useMarket((s) => s.lastPrice[s.selected])
  const myOrders = useMarket((s) => s.myOrders)
  const prefill = useMarket((s) => s.prefillTicket)
  const xray = useMarket((s) => s.xray)
  const cases = useMarket((s) => s.cases)
  const suspects = new Set(cases.filter((c) => c.status === 'OPEN' && c.symbol === selected).map((c) => c.traderId))

  // Compare with the previous snapshot of this stock to see what was eaten.
  const previous = useRef<{ symbol: string; bids: Level[]; asks: Level[] } | null>(null)
  const prev = previous.current?.symbol === selected ? previous.current : null
  const bidChanges = sideChanges(prev?.bids, book?.bids ?? [], 'bid')
  const askChanges = sideChanges(prev?.asks, book?.asks ?? [], 'ask')
  const [ghosts, setGhosts] = useState<Ghost[]>([])
  useEffect(() => {
    if (!book) return
    const now = Date.now()
    const added = [...bidChanges.gone.map((level) => ({ side: 'bid' as const, level, until: now + GONE_MS })),
      ...askChanges.gone.map((level) => ({ side: 'ask' as const, level, until: now + GONE_MS }))]
    previous.current = { symbol: book.symbol, bids: book.bids, asks: book.asks }
    if (added.length) setGhosts((g) => [...g.filter((x) => x.until > now), ...added].slice(-8))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [book])
  useEffect(() => {
    if (!ghosts.length) return
    const timer = setTimeout(() => setGhosts((g) => g.filter((x) => x.until > Date.now())), GONE_MS)
    return () => clearTimeout(timer)
  }, [ghosts])
  useEffect(() => setGhosts([]), [selected])

  const bids = book?.bids ?? []
  const asks = [...(book?.asks ?? [])].reverse()
  const ghostBids = ghosts.filter((g) => g.side === 'bid' && !bids.some((l) => l.price === g.level.price)).map((g) => g.level)
  const ghostAsks = ghosts.filter((g) => g.side === 'ask' && !asks.some((l) => l.price === g.level.price)).map((g) => g.level)
  const shownBids = [...bids, ...ghostBids].sort((a, b) => b.price - a.price)
  const shownAsks = [...asks, ...ghostAsks].sort((a, b) => b.price - a.price)
  const maxQty = Math.max(1, ...bids.map((l) => l.totalQty), ...asks.map((l) => l.totalQty))
  const myPrices = new Set(Object.values(myOrders)
    .filter((o) => o.symbol === selected && isWorking(o)).map((o) => `${o.side}:${o.limitPrice}`))
  const spread = bids[0] && book?.asks[0] ? book.asks[0].price - bids[0].price : undefined

  /* A level that has just been eaten sends its size to the tape: the chit
     leaves from the row itself, which is still on screen as a ghost. */
  const fly = (side: 'bid' | 'ask', l: Level) => (el: HTMLDivElement | null) => {
    if (el) launchTicket(el, document.getElementById('dx-tape-target'), `×${formatQty(l.totalQty)}`, side)
  }

  const row = (side: 'bid' | 'ask', best: boolean) => (l: Level) => {
    const isGhost = (side === 'bid' ? ghostBids : ghostAsks).includes(l)
    const eaten = (side === 'bid' ? bidChanges : askChanges).hits.get(l.price)
    return (
    <div key={side + l.price} ref={isGhost ? fly(side, l) : undefined}
      className={`dx-book-line is-${side}${isGhost ? ' is-gone' : ''}`}>
      {eaten !== undefined && !isGhost && <span key={`${l.price}-${l.totalQty}`} className="dx-book-bite" style={{ ['--bite-share' as string]: Math.max(0.12, eaten).toFixed(2) }} aria-hidden="true" />}
    <OrderBookRow side={side} price={toRupees(l.price)} qty={l.totalQty} orders={l.orderCount}
      depth={l.totalQty / maxQty} best={best && l === (side === 'bid' ? bids[0] : asks[asks.length - 1])}
      mine={myPrices.has(`${side === 'bid' ? 'BUY' : 'SELL'}:${l.price}`)}
      onClick={() => prefill(l.price)} flagged={!!l.mainTrader && suspects.has(l.mainTrader)} xray={xray} bot={xray && l.mainTrader ? botKey(l.mainTrader) ?? l.mainTrader.slice(0, 2) : undefined} />
    </div>
    )
  }

  return (
    <RuledPanel label="Order Book" meta={`${selected} · 10 levels`} columnRule="left"
      state={!book ? 'loading' : bids.length + asks.length ? 'ready' : 'empty'}
      emptyText="The book is empty. Your limit order would be the first line."
      style={{ minWidth: 0 }}>
      <div style={{ ...header, display: 'grid', gridTemplateColumns: '14px minmax(58px,1fr) minmax(42px,0.8fr) 32px minmax(0,96px)', columnGap: 8, padding: '2px 6px 2px 4px' }}>
        <span /><span style={{ textAlign: 'right' }}>▼ Ask</span><span style={{ textAlign: 'right' }}>Qty</span><span style={{ textAlign: 'right' }}>{xray ? 'Bot' : 'Ord'}</span><span>Depth</span>
      </div>
      {shownAsks.map(row('ask', true))}
      <div className="dx-num" style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--text-label)', color: 'var(--ink-muted)', padding: '4px 6px', borderBottom: '1px solid var(--rule-strong)', borderTop: '1px solid var(--rule-strong)' }}>
        <span>Last {last !== undefined ? '₹' + formatPaise(last) : '—'}</span>
        <span>Spread {spread !== undefined ? '₹' + formatPaise(spread) : '—'}</span>
      </div>
      {shownBids.map(row('bid', true))}
      <div style={{ ...header, padding: '3px 4px' }}>▲ Bids</div>
    </RuledPanel>
  )
}
