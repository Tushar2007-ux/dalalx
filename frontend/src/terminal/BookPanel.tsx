import { OrderBookRow, RuledPanel } from '../design'
import { formatPaise, toRupees } from '../lib/format'
import { botKey, isWorking } from '../lib/market'
import type { Level } from '../lib/types'
import { useMarket } from '../store/market'

const header = { fontFamily: 'var(--font-mono)', fontSize: 10.5, color: 'var(--ink-muted)' }

/**
 * Right-centre column: ten levels each side. Asks are printed highest first so
 * the two best prices meet in the middle, with the spread between them.
 * Clicking a level copies its price into the order ticket.
 */
export function BookPanel() {
  const selected = useMarket((s) => s.selected)
  const book = useMarket((s) => s.books[s.selected])
  const last = useMarket((s) => s.lastPrice[s.selected])
  const myOrders = useMarket((s) => s.myOrders)
  const prefill = useMarket((s) => s.prefillTicket)
  const xray = useMarket((s) => s.xray)

  const bids = book?.bids ?? []
  const asks = [...(book?.asks ?? [])].reverse()
  const maxQty = Math.max(1, ...bids.map((l) => l.totalQty), ...asks.map((l) => l.totalQty))
  const myPrices = new Set(Object.values(myOrders)
    .filter((o) => o.symbol === selected && isWorking(o)).map((o) => `${o.side}:${o.limitPrice}`))
  const spread = bids[0] && book?.asks[0] ? book.asks[0].price - bids[0].price : undefined

  const row = (side: 'bid' | 'ask', best: boolean) => (l: Level) => (
    <OrderBookRow key={side + l.price} side={side} price={toRupees(l.price)} qty={l.totalQty} orders={l.orderCount}
      depth={l.totalQty / maxQty} best={best && l === (side === 'bid' ? bids[0] : asks[asks.length - 1])}
      mine={myPrices.has(`${side === 'bid' ? 'BUY' : 'SELL'}:${l.price}`)}
      onClick={() => prefill(l.price)} xray={xray} bot={xray && l.mainTrader ? botKey(l.mainTrader) ?? l.mainTrader.slice(0, 2) : undefined} />
  )

  return (
    <RuledPanel label="Order Book" meta={`${selected} · 10 levels`} columnRule="left"
      state={!book ? 'loading' : bids.length + asks.length ? 'ready' : 'empty'}
      emptyText="The book is empty. Your limit order would be the first line."
      style={{ minWidth: 0, padding: '0 12px', flex: '1 1 300px' }}>
      <div style={{ ...header, display: 'grid', gridTemplateColumns: '14px minmax(0,1fr) minmax(0,1fr) 36px 96px', columnGap: 10, padding: '2px 6px 2px 4px' }}>
        <span /><span style={{ textAlign: 'right' }}>▼ Ask</span><span style={{ textAlign: 'right' }}>Qty</span><span style={{ textAlign: 'right' }}>{xray ? 'Bot' : 'Ord'}</span><span>Depth</span>
      </div>
      {asks.map(row('ask', true))}
      <div className="dx-num" style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--ink-muted)', padding: '4px 6px', borderBottom: '1px solid var(--rule-strong)', borderTop: '1px solid var(--rule-strong)' }}>
        <span>Last {last !== undefined ? '₹' + formatPaise(last) : '—'}</span>
        <span>Spread {spread !== undefined ? '₹' + formatPaise(spread) : '—'}</span>
      </div>
      {bids.map(row('bid', true))}
      <div style={{ ...header, padding: '3px 4px' }}>▲ Bids</div>
    </RuledPanel>
  )
}
