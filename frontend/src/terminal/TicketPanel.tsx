import { useEffect, useRef, useState } from 'react'
import {
  AlertNotice, Button, MarginNote, Modal, OrderTypeToggle, PriceInput, RubberStamp, RuledPanel, SectionLabel, SideToggle, XRayToggle,
} from '../design'
import { api, ApiError } from '../lib/api'
import { clockTime, formatPaise, formatQty, parseRupees } from '../lib/format'
import { isWorking } from '../lib/market'
import type { OrderType, OrderView, PlaceOrder } from '../lib/types'
import { useMarket } from '../store/market'
import { BotsSection } from './BotsSection'
import { bandOf, checkTicket, ORDER_TYPES, outcomeToast, workingLine } from './ticket'

const italic = { margin: 0, fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 'var(--text-body)', color: 'var(--ink-muted)' } as const

/** Right column: the order ticket (with a confirm step) and the trader's own working orders. */
export function TicketPanel() {
  const selected = useMarket((s) => s.selected)
  const connection = useMarket((s) => s.connection)
  const halted = useMarket((s) => s.status[s.selected]?.phase === 'HALTED')
  const auction = useMarket((s) => s.status[s.selected]?.phase === 'AUCTION')
  return (
    <RuledPanel label="Order Ticket" meta={selected} columnRule="left"
      style={{ minWidth: 0 }}>
      {connection === 'offline'
        ? <ClosedTicket />
        : halted ? <HaltedTicket symbol={selected} /> : <Ticket key={selected} symbol={selected} auction={auction} />}
      <AccountLine />
      <XRaySwitch />
      <YourOrders />
      <SurveillanceSection />
      <BotsSection />
    </RuledPanel>
  )
}

function ClosedTicket() {
  return (
    <div style={{ display: 'grid', gap: 'var(--space-2)' }}>
      <SideToggle value="buy" disabled />
      <OrderTypeToggle value="limit" disabled showHint={false} />
      <Button fullWidth size="lg" disabled>Market closed · exchange offline</Button>
      <p className="dx-deck">Orders reopen as soon as the exchange answers again.</p>
    </div>
  )
}

function HaltedTicket({ symbol }: { symbol: string }) {
  return (
    <div style={{ display: 'grid', gap: 'var(--space-2)' }}>
      <SideToggle value="buy" disabled />
      <OrderTypeToggle value="limit" disabled showHint={false} />
      <Button fullWidth size="lg" disabled>Order entry paused · {symbol} halted</Button>
      <p className="dx-deck">New orders are refused until the halt ends. Your resting orders stay in the book, and you can still cancel them below.</p>
    </div>
  )
}

const AUCTION_ONLY = [{ value: 'limit', label: 'Limit only', hint: 'During the call auction only limit orders are accepted. Everything that crosses trades at one price when it closes.' }]

function Ticket({ symbol, auction = false }: { symbol: string; auction?: boolean }) {
  const reference = useMarket((s) => s.symbols.find((x) => x.symbol === symbol)?.referencePrice)
  const last = useMarket((s) => s.lastPrice[symbol])
  const prefill = useMarket((s) => s.ticketPrefill)
  const { trackOrder, toast } = useMarket.getState()

  const [side, setSide] = useState<'buy' | 'sell'>('buy')
  const [type, setType] = useState<OrderType>('LIMIT')
  const [priceText, setPriceText] = useState(() => (last ?? reference) ? formatPaise((last ?? reference)!) : '')
  const [qtyText, setQtyText] = useState('10')
  const [displayText, setDisplayText] = useState('100')
  const [confirm, setConfirm] = useState<PlaceOrder | null>(null)
  const [sending, setSending] = useState(false)
  const [stamp, setStamp] = useState<string | null>(null)
  // A resting order of mine that someone else just hit gets the same stamp as an order that filled at once.
  const restingFill = useMarket((s) => s.fillStamp)
  useEffect(() => {
    if (!restingFill) return
    setStamp(restingFill.text)
    useMarket.getState().clearFillStamp()
  }, [restingFill])

  // The FILLED stamp lifts off after a few seconds so it never hides the controls for long.
  useEffect(() => {
    if (!stamp) return
    const timer = setTimeout(() => setStamp(null), 4000)
    return () => clearTimeout(timer)
  }, [stamp])

  // If the ticket opened before prices arrived, fill in the price once they do (unless the trader already typed).
  const typed = useRef(false)
  const marketPrice = last ?? reference
  useEffect(() => {
    if (!typed.current && marketPrice !== undefined) setPriceText((t) => t || formatPaise(marketPrice))
  }, [marketPrice])

  // B / S on the keyboard choose the side.
  const keyedSide = useMarket((s) => s.ticketSide)
  useEffect(() => {
    if (keyedSide) setSide(keyedSide.side)
  }, [keyedSide])

  // A click on a book level copies its price here.
  useEffect(() => {
    if (prefill) setPriceText(formatPaise(prefill.price))
  }, [prefill])

  // Keep the trader's chosen type for later, but an auction only takes limit orders.
  useEffect(() => {
    if (auction) setType('LIMIT')
  }, [auction])
  // The ±10% band is measured from the last traded price, so it follows the market after a big move.
  const bandCentre = last ?? reference
  const check = checkTicket({ symbol, side: side === 'buy' ? 'BUY' : 'SELL', type, priceText, qtyText, displayText, reference: bandCentre })
  const estimate = check.order ? (type === 'MARKET' ? (last ?? reference ?? 0) : check.order.price) * check.order.qty : 0
  const band = bandCentre ? bandOf(bandCentre) : undefined

  const send = async () => {
    if (!confirm) return
    setSending(true)
    try {
      const result = await api.placeOrder(confirm)
      trackOrder(result.order)
      toast({ ...outcomeToast(result), time: clockTime(Date.now()).slice(0, 8) })
      if (result.trades.length) {
        const filled = result.order.originalQty - result.order.remainingQty
        setStamp(`${clockTime(Date.now()).slice(0, 8)} · ${formatQty(filled)} @ ${formatPaise(result.trades[result.trades.length - 1].price)}`)
      }
    } catch (e) {
      toast({ kind: 'rejected', title: 'Order not sent', detail: e instanceof ApiError ? e.message : 'The exchange did not answer' })
    } finally {
      setSending(false)
      setConfirm(null)
    }
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr)', gap: 'var(--space-3)', position: 'relative' }}
      onKeyDown={(e) => { if (e.key === 'Enter' && check.order && !confirm) setConfirm(check.order) }}>
      <SideToggle value={side} onChange={setSide} />
      <OrderTypeToggle value={auction ? 'limit' : type.toLowerCase()} onChange={(v) => setType(v.toUpperCase() as OrderType)}
        options={auction ? AUCTION_ONLY : ORDER_TYPES} />
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1fr)', gap: 'var(--space-2)' }}>
        <PriceInput label={type === 'STOP_LOSS' ? 'Trigger price' : 'Limit price'} prefix="₹" value={type === 'MARKET' ? 'Market' : priceText} onChange={(v) => { typed.current = true; setPriceText(v) }}
          disabled={type === 'MARKET'} inputMode="decimal"
          error={type !== 'MARKET' && check.error && !check.error.startsWith('Quantity') && !check.error.startsWith('Show') ? check.error : undefined}
          hint={type === 'MARKET' ? 'Best available' : band ? `Band ₹${formatPaise(band[0])}–${formatPaise(band[1])}` : undefined} />
        <PriceInput label="Quantity" suffix="qty" value={qtyText} onChange={setQtyText} inputMode="numeric"
          error={check.error?.startsWith('Quantity') ? check.error : undefined} hint="Lot size 1" />
      </div>
      {type === 'ICEBERG' && (
        <PriceInput label="Show at a time" suffix="qty" value={displayText} onChange={setDisplayText} inputMode="numeric"
          error={check.error?.startsWith('Show') ? check.error : undefined}
          hint="The rest stays hidden; each new slice joins the back of the queue" />
      )}
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--text-small)', color: 'var(--ink-muted)', borderTop: '1px solid var(--rule)', paddingTop: 'var(--space-2)' }}>
        <span>Order value{type === 'MARKET' ? ' (est.)' : ''}</span>
        <span className="dx-num" style={{ color: 'var(--ink)', fontSize: 'var(--text-ui)' }}>₹{formatPaise(estimate)}</span>
      </div>
      <Button variant={side === 'sell' ? 'danger' : 'primary'} fullWidth size="lg" kbd="↵"
        disabled={!check.order || sending} loading={sending} onClick={() => check.order && setConfirm(check.order)}>
        {side === 'buy' ? '▲ Buy' : '▼ Sell'} {qtyText} {symbol}
      </Button>
      {stamp && <RubberStamp kind="filled" sub={stamp} animate slamKey={stamp} angle={-5} backed
        style={{ position: 'absolute', right: 6, top: 54, pointerEvents: 'none' }} />}

      <Modal open={!!confirm} kicker="Confirm order" onClose={() => setConfirm(null)}
        title={confirm ? `${confirm.side === 'BUY' ? 'Buy' : 'Sell'} ${formatQty(confirm.qty)} ${symbol}${confirm.type === 'MARKET' ? ' at market?'
          : confirm.type === 'STOP_LOSS' ? ` if the price reaches ₹${formatPaise(confirm.price)}?` : ` at ₹${formatPaise(confirm.price)}?`}` : ''}
        actions={<>
          <Button variant="secondary" onClick={() => setConfirm(null)}>Keep editing</Button>
          <Button variant={confirm?.side === 'SELL' ? 'danger' : 'primary'} onClick={() => void send()} loading={sending}>
            {confirm?.side === 'SELL' ? '▼ Sell' : '▲ Buy'}
          </Button>
        </>}>
        {confirm && <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: '4px 16px' }}>
          <span style={{ color: 'var(--ink-muted)' }}>Type</span><span>{confirm.type.replace('_', '-')}</span>
          {confirm.displayQty && <><span style={{ color: 'var(--ink-muted)' }}>Shown at a time</span><span className="dx-num">{formatQty(confirm.displayQty)}</span></>}
          <span style={{ color: 'var(--ink-muted)' }}>Estimated value</span><span className="dx-num">₹{formatPaise(estimate)}</span>
        </div>}
      </Modal>
    </div>
  )
}

/** X-Ray mode: the book shows whose orders are at each price, plus a link to the step-by-step explainer. */
function XRaySwitch() {
  const xray = useMarket((s) => s.xray)
  const setXray = useMarket((s) => s.setXray)
  return (
    <div style={{ marginTop: 'var(--space-3)', display: 'grid', gap: 'var(--space-1)' }}>
      <XRayToggle on={xray} onChange={setXray} />
      {xray && <MarginNote tone="ink" side="below">
        Each book level now shows the stamp of the trader behind it. <a href="#/xray">See how your last order was matched →</a>
      </MarginNote>}
    </div>
  )
}

/** The Inspector's latest open case, with a link to the full case file. */
function SurveillanceSection() {
  const cases = useMarket((s) => s.cases)
  const open = cases.filter((c) => c.status === 'OPEN')
  const latest = open[0]
  return (
    <div style={{ marginTop: 'var(--space-4)' }}>
      <SectionLabel meta={open.length ? <span style={{ color: 'var(--pencil-text)' }}>◆ {open.length} open</span> : 'All quiet'}>Surveillance</SectionLabel>
      {latest
        ? <div style={{ margin: '6px 0 4px' }}>
            <AlertNotice severity={latest.severity === 'CRITICAL' ? 'critical' : latest.severity === 'WARNING' ? 'warning' : 'notice'}
              fresh title={`${latest.ruleName} · ${latest.symbol}`} reference={latest.id} time={clockTime(latest.openedAt).slice(0, 8)}>
              {latest.summary}. <a href="#/surveillance">Open the case file →</a>
            </AlertNotice>
          </div>
        : <p style={{ ...italic, fontSize: 'var(--text-ui)', margin: '6px 0' }}>The Inspector reads every line of the tape. Nothing to report.</p>}
    </div>
  )
}

/** When signed in, who the ticket is trading for and how much cash is free. */
function AccountLine() {
  const account = useMarket((s) => s.account)
  if (!account) {
    return <p style={{ ...italic, fontSize: 'var(--text-small)', margin: 'var(--space-2) 0 0' }}>
      Trading as the house desk. <a href="#/account">Sign in</a> to trade your own ₹10,00,000.
    </p>
  }
  return (
    <p className="dx-num" style={{ margin: 'var(--space-2) 0 0', fontSize: 'var(--text-small)' }}>
      Trading as <b>{account.name}</b> · ₹{formatPaise(account.availableCash)} free · <a href="#/account">statement</a>
    </p>
  )
}

/** The trader's own orders that can still trade, newest first, each with edit and cancel. */
function YourOrders() {
  const myOrders = useMarket((s) => s.myOrders)
  const working = Object.values(myOrders).filter(isWorking).sort((a, b) => b.id - a.id)

  const cancel = async (id: number) => {
    const { trackOrder, toast } = useMarket.getState()
    try {
      const reply = await api.cancelOrder(id)
      if (reply.order) trackOrder(reply.order)
      toast(reply.cancelled
        ? { kind: 'cancelled', title: `Order #${id} cancelled`, detail: reply.order ? `${formatQty(reply.order.remainingQty)} ${reply.order.symbol} withdrawn` : undefined }
        : { kind: 'rejected', title: `Could not cancel #${id}`, detail: reply.reason ?? undefined })
    } catch (e) {
      toast({ kind: 'rejected', title: `Could not cancel #${id}`, detail: e instanceof ApiError ? e.message : 'The exchange did not answer' })
    }
  }

  return (
    <div style={{ marginTop: 'var(--space-4)' }}>
      <SectionLabel count={working.length} meta="Resting in the book">Your Orders</SectionLabel>
      {working.length === 0 && <p style={{ ...italic, fontSize: 'var(--text-ui)', margin: '6px 0' }}>Nothing resting. Limit orders that don&apos;t fill at once will wait here.</p>}
      {working.map((o) => <WorkingOrder key={o.id} order={o} onCancel={() => void cancel(o.id)} />)}
    </div>
  )
}

/**
 * One working order. "Edit" opens the price and size in place: a smaller size
 * at the same price keeps the order's place in the queue; any other change
 * re-enters it at the back (the exchange says which happened).
 */
function WorkingOrder({ order: o, onCancel }: { order: OrderView; onCancel: () => void }) {
  const [editing, setEditing] = useState(false)
  const [priceText, setPriceText] = useState(formatPaise(o.limitPrice))
  const [qtyText, setQtyText] = useState(String(o.remainingQty))
  const [saving, setSaving] = useState(false)

  const save = async () => {
    const { trackOrder, toast } = useMarket.getState()
    const price = parseRupees(priceText)
    const qty = /^\d+$/.test(qtyText.trim()) ? Number(qtyText.trim()) : NaN
    if (price === null || !(qty > 0)) {
      toast({ kind: 'rejected', title: 'Check the new price and size' })
      return
    }
    setSaving(true)
    try {
      const reply = await api.amendOrder(o.id, price, qty)
      if (reply.order) trackOrder(reply.order)
      if (reply.amended && !reply.keptPriority) trackOrder({ ...o, status: 'CANCELLED' })
      toast(reply.amended
        ? { kind: 'info', title: `Order #${o.id} amended`, detail: reply.keptPriority ? 'Smaller, same price: it kept its place in the queue' : `Re-entered as #${reply.order?.id} at the back of the queue` }
        : { kind: 'rejected', title: `Could not amend #${o.id}`, detail: reply.reason ?? undefined })
      setEditing(false)
    } catch (e) {
      toast({ kind: 'rejected', title: `Could not amend #${o.id}`, detail: e instanceof ApiError ? e.message : 'The exchange did not answer' })
    } finally {
      setSaving(false)
    }
  }

  const row = { display: 'grid', gridTemplateColumns: 'auto minmax(0,1fr) auto auto', gap: 'var(--space-2)', alignItems: 'center', padding: '4px 0', borderBottom: '1px solid var(--rule)', fontSize: 'var(--text-small)' } as const
  if (!editing) {
    return (
      <div className="dx-num" style={row}>
        <span style={{ color: o.side === 'BUY' ? 'var(--buy)' : 'var(--sell)', fontWeight: 600 }}>{o.side === 'BUY' ? '▲' : '▼'} {o.symbol}</span>
        <span style={{ minWidth: 0 }}>{workingLine(o)}</span>
        <Button size="sm" variant="ghost" onClick={() => setEditing(true)}>Edit</Button>
        <Button size="sm" variant="ghost" onClick={onCancel}>Cancel</Button>
      </div>
    )
  }
  return (
    <div style={{ display: 'grid', gap: 'var(--space-2)', padding: 'var(--space-2) 0', borderBottom: '1px solid var(--rule)' }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1fr)', gap: 'var(--space-2)' }}>
        <PriceInput label={o.type === 'STOP_LOSS' ? 'New trigger' : 'New price'} prefix="₹" value={priceText} onChange={setPriceText} inputMode="decimal" />
        <PriceInput label="New size" suffix="qty" value={qtyText} onChange={setQtyText} inputMode="numeric" />
      </div>
      <div style={{ display: 'flex', gap: 'var(--space-2)', justifyContent: 'flex-end' }}>
        <Button size="sm" variant="secondary" onClick={() => setEditing(false)}>Keep</Button>
        <Button size="sm" variant="primary" loading={saving} onClick={() => void save()}>Amend #{o.id}</Button>
      </div>
    </div>
  )
}
