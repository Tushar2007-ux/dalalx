import { useEffect, useRef, useState } from 'react'
import {
  AlertNotice, Button, MarginNote, Modal, OrderTypeToggle, PriceInput, RubberStamp, RuledPanel, SectionLabel, SideToggle, XRayToggle,
} from '../design'
import { api, ApiError } from '../lib/api'
import { clockTime, formatPaise, formatQty } from '../lib/format'
import { isWorking } from '../lib/market'
import type { OrderType, PlaceOrder } from '../lib/types'
import { useMarket } from '../store/market'
import { BotsSection } from './BotsSection'
import { bandOf, checkTicket, outcomeToast } from './ticket'

const italic = { margin: 0, fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 14, color: 'var(--ink-muted)' } as const

/** Right column: the order ticket (with a confirm step) and the trader's own working orders. */
export function TicketPanel() {
  const selected = useMarket((s) => s.selected)
  const connection = useMarket((s) => s.connection)
  const halted = useMarket((s) => s.status[s.selected]?.phase === 'HALTED')
  return (
    <RuledPanel label="Order Ticket" meta={selected} columnRule="left"
      style={{ minWidth: 0, padding: '0 0 0 12px', flex: '1 1 300px' }}>
      {connection === 'offline'
        ? <ClosedTicket />
        : halted ? <HaltedTicket symbol={selected} /> : <Ticket key={selected} symbol={selected} />}
      <XRaySwitch />
      <YourOrders />
      <SurveillanceSection />
      <BotsSection />
    </RuledPanel>
  )
}

function ClosedTicket() {
  return (
    <div style={{ display: 'grid', gap: 10 }}>
      <SideToggle value="buy" disabled />
      <OrderTypeToggle value="limit" disabled showHint={false} />
      <Button fullWidth size="lg" disabled>Market closed · exchange offline</Button>
      <p style={italic}>Orders reopen as soon as the exchange answers again.</p>
    </div>
  )
}

function HaltedTicket({ symbol }: { symbol: string }) {
  return (
    <div style={{ display: 'grid', gap: 10 }}>
      <SideToggle value="buy" disabled />
      <OrderTypeToggle value="limit" disabled showHint={false} />
      <Button fullWidth size="lg" disabled>Order entry paused · {symbol} halted</Button>
      <p style={italic}>New orders are refused until the halt ends. Your resting orders stay in the book, and you can still cancel them below.</p>
    </div>
  )
}

function Ticket({ symbol }: { symbol: string }) {
  const reference = useMarket((s) => s.symbols.find((x) => x.symbol === symbol)?.referencePrice)
  const last = useMarket((s) => s.lastPrice[symbol])
  const prefill = useMarket((s) => s.ticketPrefill)
  const { trackOrder, toast } = useMarket.getState()

  const [side, setSide] = useState<'buy' | 'sell'>('buy')
  const [type, setType] = useState<OrderType>('LIMIT')
  const [priceText, setPriceText] = useState(() => (last ?? reference) ? formatPaise((last ?? reference)!) : '')
  const [qtyText, setQtyText] = useState('10')
  const [confirm, setConfirm] = useState<PlaceOrder | null>(null)
  const [sending, setSending] = useState(false)
  const [stamp, setStamp] = useState<string | null>(null)

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

  // A click on a book level copies its price here.
  useEffect(() => {
    if (prefill) setPriceText(formatPaise(prefill.price))
  }, [prefill])

  const check = checkTicket({ symbol, side: side === 'buy' ? 'BUY' : 'SELL', type, priceText, qtyText, reference })
  const estimate = check.order ? (type === 'MARKET' ? (last ?? reference ?? 0) : check.order.price) * check.order.qty : 0
  const band = reference ? bandOf(reference) : undefined

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
    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr)', gap: 12, position: 'relative' }}
      onKeyDown={(e) => { if (e.key === 'Enter' && check.order && !confirm) setConfirm(check.order) }}>
      <SideToggle value={side} onChange={setSide} />
      <OrderTypeToggle value={type.toLowerCase()} onChange={(v) => setType(v.toUpperCase() as OrderType)} />
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1fr)', gap: 10 }}>
        <PriceInput label="Limit price" prefix="₹" value={type === 'MARKET' ? 'Market' : priceText} onChange={(v) => { typed.current = true; setPriceText(v) }}
          disabled={type === 'MARKET'} inputMode="decimal"
          error={type !== 'MARKET' && check.error && !check.error.startsWith('Quantity') ? check.error : undefined}
          hint={type === 'MARKET' ? 'Best available' : band ? `Band ₹${formatPaise(band[0])}–${formatPaise(band[1])}` : undefined} />
        <PriceInput label="Quantity" suffix="qty" value={qtyText} onChange={setQtyText} inputMode="numeric"
          error={check.error?.startsWith('Quantity') ? check.error : undefined} hint="Lot size 1" />
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'var(--ink-muted)', borderTop: '1px solid var(--rule)', paddingTop: 8 }}>
        <span>Order value{type === 'MARKET' ? ' (est.)' : ''}</span>
        <span className="dx-num" style={{ color: 'var(--ink)', fontSize: 13 }}>₹{formatPaise(estimate)}</span>
      </div>
      <Button variant={side === 'sell' ? 'danger' : 'primary'} fullWidth size="lg" kbd="↵"
        disabled={!check.order || sending} loading={sending} onClick={() => check.order && setConfirm(check.order)}>
        {side === 'buy' ? '▲ Buy' : '▼ Sell'} {qtyText} {symbol}
      </Button>
      {stamp && <RubberStamp kind="filled" sub={stamp} animate slamKey={stamp} angle={-5} backed
        style={{ position: 'absolute', right: 6, top: 54, pointerEvents: 'none' }} />}

      <Modal open={!!confirm} kicker="Confirm order" onClose={() => setConfirm(null)}
        title={confirm ? `${confirm.side === 'BUY' ? 'Buy' : 'Sell'} ${formatQty(confirm.qty)} ${symbol}${confirm.type === 'MARKET' ? ' at market?' : ` at ₹${formatPaise(confirm.price)}?`}` : ''}
        actions={<>
          <Button variant="secondary" onClick={() => setConfirm(null)}>Keep editing</Button>
          <Button variant={confirm?.side === 'SELL' ? 'danger' : 'primary'} onClick={() => void send()} loading={sending}>
            {confirm?.side === 'SELL' ? '▼ Sell' : '▲ Buy'}
          </Button>
        </>}>
        {confirm && <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: '4px 16px' }}>
          <span style={{ color: 'var(--ink-muted)' }}>Type</span><span>{confirm.type}</span>
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
    <div style={{ marginTop: 12, display: 'grid', gap: 6 }}>
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
    <div style={{ marginTop: 16 }}>
      <SectionLabel meta={open.length ? <span style={{ color: 'var(--pencil-text)' }}>◆ {open.length} open</span> : 'All quiet'}>Surveillance</SectionLabel>
      {latest
        ? <div style={{ margin: '6px 0 4px' }}>
            <AlertNotice severity={latest.severity === 'CRITICAL' ? 'critical' : latest.severity === 'WARNING' ? 'warning' : 'notice'}
              fresh title={`${latest.ruleName} · ${latest.symbol}`} reference={latest.id} time={clockTime(latest.openedAt).slice(0, 8)}>
              {latest.summary}. <a href="#/surveillance">Open the case file →</a>
            </AlertNotice>
          </div>
        : <p style={{ ...italic, fontSize: 13, margin: '6px 0' }}>The Inspector reads every line of the tape. Nothing to report.</p>}
    </div>
  )
}

/** The trader's own orders that can still trade, newest first, each with a cancel button. */
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
    <div style={{ marginTop: 16 }}>
      <SectionLabel count={working.length} meta="Resting in the book">Your Orders</SectionLabel>
      {working.length === 0 && <p style={{ ...italic, fontSize: 13, margin: '6px 0' }}>Nothing resting. Limit orders that don&apos;t fill at once will wait here.</p>}
      {working.map((o) => (
        <div key={o.id} className="dx-num" style={{ display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 8, alignItems: 'center', padding: '4px 0', borderBottom: '1px solid var(--rule)', fontSize: 12 }}>
          <span style={{ color: o.side === 'BUY' ? 'var(--buy)' : 'var(--sell)', fontWeight: 600 }}>{o.side === 'BUY' ? '▲' : '▼'} {o.symbol}</span>
          <span>{formatQty(o.remainingQty)}/{formatQty(o.originalQty)} @ ₹{formatPaise(o.limitPrice)}</span>
          <Button size="sm" variant="ghost" onClick={() => void cancel(o.id)}>Cancel</Button>
        </div>
      ))}
    </div>
  )
}
