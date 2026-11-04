import { useEffect, useState } from 'react'
import { Button, Monogram, OrderTypeToggle, RedPencilCircle, RubberStamp, SectionLabel } from '../design'
import { api, ApiError } from '../lib/api'
import { clockTime, formatPaise, formatQty } from '../lib/format'
import { botKey } from '../lib/market'
import { monogramOf } from '../lib/monogram'
import type { XRay } from '../lib/types'
import { Shell } from '../shell/Shell'
import type { Headline } from '../terminal/headline'
import { CIRCLED, mainTraderOf, xrayView } from './xrayView'

const label = { fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase' } as const
const italic = { fontFamily: 'var(--font-display)', fontStyle: 'italic' } as const
const COLS = '34px minmax(90px,128px) minmax(110px,150px) 30px minmax(60px,120px) minmax(0,1fr)'
const SPEEDS = [{ value: '0.5', label: '0.5×' }, { value: '1', label: '1×' }, { value: '2', label: '2×' }]
const STEP_MS = 1600

/** Stamp for a trader: a known bot's monogram, YOU, or the first two letters. */
function stampOf(traderId: string): string {
  return botKey(traderId) ?? monogramOf(traderId)
}

/**
 * The X-Ray explainer: replays how your most recent order was matched against
 * the book as it stood when it arrived, one step at a time, with the server's
 * narration beside it. Layout follows design/export/ui_kits/xray.
 */
export function XRayPage() {
  const [xray, setXray] = useState<XRay | null>(null)
  const [recent, setRecent] = useState<XRay[]>([])
  const [error, setError] = useState<string | null>(null)
  const [step, setStep] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [speed, setSpeed] = useState('1')

  useEffect(() => {
    api.recentXRays(10).then((list) => {
      setRecent(list)
      if (list.length) { setXray(list[0]); setStep(0); setPlaying(true) } else setError('none')
    }).catch((e) => setError(e instanceof ApiError ? e.message : 'unreachable'))
  }, [])

  const last = xray ? xray.steps.length - 1 : 0
  useEffect(() => {
    if (!playing || !xray) return
    if (step >= last) { setPlaying(false); return }
    const timer = setTimeout(() => setStep((s) => s + 1), STEP_MS / Number(speed))
    return () => clearTimeout(timer)
  }, [playing, step, speed, last, xray])

  const choose = (id: number) => {
    const next = recent.find((x) => x.orderId === id)
    if (next) { setXray(next); setStep(0); setPlaying(true) }
  }

  const headline: Headline = xray
    ? { kicker: 'X-Ray', state: 'live', tone: 'neutral', headline: `How your order to ${xray.side === 'BUY' ? 'buy' : 'sell'} ${formatQty(xray.qty)} ${xray.symbol} was matched`, deck: 'A replay of the book as it stood the moment your order arrived. Step through it at your own pace.' }
    : { kicker: 'X-Ray', state: 'live', tone: 'neutral', headline: 'X-Ray: see exactly how an order is matched', deck: 'Place an order on the Trading Floor, then come back here to watch it meet the book.' }

  return (
    <Shell route="xray" headline={headline} page={2} section="X-Ray">
      {!xray
        ? <EmptyXRay error={error} />
        : <>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'end', justifyContent: 'space-between', padding: '12px 0', borderBottom: '1px solid var(--rule)' }}>
            <div style={{ display: 'flex', gap: 16, alignItems: 'end', flexWrap: 'wrap' }}>
              <div style={{ display: 'grid', gap: 4 }}><span style={label}>Speed</span><OrderTypeToggle value={speed} onChange={setSpeed} options={SPEEDS} showHint={false} style={{ width: 200 }} /></div>
              <Button variant="primary" onClick={() => { if (step >= last) setStep(0); setPlaying(!playing) }}>{playing ? '❚❚ Pause' : step >= last ? '↺ Replay' : '▶ Play'}</Button>
              <Button variant="secondary" disabled={step >= last} onClick={() => { setPlaying(false); setStep((s) => Math.min(last, s + 1)) }}>Step →</Button>
              <Button variant="ghost" disabled={step === 0} onClick={() => { setPlaying(false); setStep((s) => Math.max(0, s - 1)) }}>← Back</Button>
              <span className="dx-num" style={{ fontSize: 13, color: 'var(--ink-muted)', paddingBottom: 9 }}>Step {step + 1} of {xray.steps.length}</span>
            </div>
            <div style={{ display: 'flex', gap: 14, alignItems: 'end' }}>
              {recent.length > 1 && <label style={{ display: 'grid', gap: 4 }}>
                <span style={label}>Your recent orders</span>
                <select value={xray.orderId} onChange={(e) => choose(Number(e.target.value))} className="dx-focus"
                  style={{ fontFamily: 'var(--font-mono)', fontSize: 12, padding: '6px 8px', background: 'var(--surface)', color: 'var(--ink)', border: '1px solid var(--rule-strong)', borderRadius: 0 }}>
                  {recent.map((x) => <option key={x.orderId} value={x.orderId}>#{x.orderId} · {x.side} {x.qty} {x.symbol} · {x.type}</option>)}
                </select>
              </label>}
              <a href="#/" style={{ ...italic, fontSize: 15, paddingBottom: 8 }}>Exit X-Ray ←</a>
            </div>
          </div>
          <XRayBody xray={xray} step={step} onStep={(s) => { setPlaying(false); setStep(s) }} />
        </>}
    </Shell>
  )
}

function EmptyXRay({ error }: { error: string | null }) {
  return (
    <div style={{ padding: '48px 0', display: 'grid', gap: 10, justifyItems: 'start', maxWidth: 560 }}>
      <SectionLabel>Nothing to X-Ray yet</SectionLabel>
      <p style={{ ...italic, fontSize: 18, margin: 0 }}>
        {error && error !== 'none' ? `The exchange is not answering (${error}).` : 'You haven’t placed an order this session.'} Send one from the order ticket and its match will be replayed here, step by step.
      </p>
      <a href="#/" style={{ ...italic, fontSize: 15 }}>Go to the Trading Floor →</a>
    </div>
  )
}

function XRayBody({ xray, step, onStep }: { xray: XRay; step: number; onStep: (s: number) => void }) {
  const view = xrayView(xray, step)
  const buying = xray.side === 'BUY'
  const sideColour = buying ? 'var(--buy)' : 'var(--sell)'
  const levels = xray.bookBefore
  const maxQty = Math.max(1, ...levels.map((l) => l.orders.reduce((a, o) => a + o.remainingQty, 0)))
  const stopLevel = (() => {
    const lastFill = xray.fills[xray.fills.length - 1]
    if (!lastFill) return 0
    const total = levels[lastFill.level]?.orders.reduce((a, o) => a + o.remainingQty, 0) ?? 0
    const taken = xray.fills.filter((f) => f.level === lastFill.level).reduce((a, f) => a + f.qty, 0)
    return taken < total ? lastFill.level : lastFill.level + 1
  })()
  const rests = view.outcomeShown && (xray.status === 'NEW' || xray.status === 'PARTIALLY_FILLED')
  // Print best price nearest the middle line, as on the floor: asks run highest → lowest.
  const order = levels.map((_, i) => i)
  const printed = buying ? [...order].reverse() : order

  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', marginTop: 14, flex: 1, gap: 24 }}>
      <section style={{ flex: '2.2 1 640px', minWidth: 0 }}>
        <SectionLabel meta={`${xray.symbol} · ${buying ? 'asks' : 'bids'} when your order arrived`}>Order Book</SectionLabel>
        <OrderSlip xray={xray} remaining={view.remaining} />
        <div style={{ display: 'grid', gridTemplateColumns: COLS, columnGap: 12, fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--ink-muted)', padding: '2px 0' }}>
          <span /><span style={{ textAlign: 'right' }}>{buying ? '▼ Ask' : '▲ Bid'}</span><span style={{ textAlign: 'right' }}>Qty</span><span>Bot</span><span>Depth</span><span>Explainer</span>
        </div>
        {levels.length === 0 && <p style={{ ...italic, color: 'var(--ink-muted)' }}>The {buying ? 'sell' : 'buy'} side of the book was empty.</p>}
        {printed.map((i) => {
          const level = levels[i]
          const total = level.orders.reduce((a, o) => a + o.remainingQty, 0)
          const taken = view.takenAt.get(i) ?? 0
          const numbers = view.fillNumbersAt.get(i) ?? []
          const isStop = view.stopShown && i === stopLevel && xray.remaining > 0 && xray.type !== 'MARKET'
          const left = total - taken
          return (
            <div key={level.price} style={{ display: 'grid', gridTemplateColumns: COLS, alignItems: 'center', columnGap: 12, minHeight: 36, borderBottom: isStop ? '2px dashed var(--pencil)' : '1px solid var(--rule)', background: numbers.length ? 'var(--surface)' : 'transparent' }}>
              <span style={{ fontFamily: 'var(--font-display)', fontSize: 20, lineHeight: 1, color: 'var(--pencil-text)', textAlign: 'center' }}>{numbers.map((n) => CIRCLED[n] ?? n + 1).join('')}</span>
              <span className="dx-num" style={{ textAlign: 'right', fontSize: 17, fontWeight: 600, color: buying ? 'var(--sell)' : 'var(--buy)' }}>
                {numbers.length ? <RedPencilCircle drawKey={`f${i}`} label="Matched level" style={{ padding: '3px 12px' }}>{formatPaise(level.price)}</RedPencilCircle> : formatPaise(level.price)}
              </span>
              <span className="dx-num" style={{ textAlign: 'right', fontSize: 17, display: 'flex', justifyContent: 'flex-end', gap: 8, alignItems: 'baseline' }}>
                {taken ? <><span style={{ textDecoration: 'line-through', textDecorationColor: 'var(--pencil)', textDecorationThickness: 2, color: 'var(--ink-muted)' }}>{formatQty(total)}</span><span style={{ fontSize: 13, color: 'var(--ink-muted)' }}>→ {formatQty(left)}</span></> : formatQty(total)}
              </span>
              <span style={{ display: 'grid', placeItems: 'center' }}><Monogram bot={stampOf(mainTraderOf(level.orders) ?? '??')} size={22} /></span>
              <span aria-hidden="true" style={{ height: 16, position: 'relative', borderRight: '1px solid var(--rule)' }}>
                <span className="dx-hatch dx-depth" style={{ position: 'absolute', right: 0, top: 0, bottom: 0, width: `${(left / maxQty) * 100}%`, minWidth: left ? 2 : 0, color: buying ? 'var(--sell)' : 'var(--buy)' }} />
              </span>
              <span style={{ ...italic, fontSize: 14, lineHeight: 1.25, color: 'var(--pencil-text)', minWidth: 0 }}>
                {numbers.length > 0 && <span className="dx-anim-slip">{numbers.map((n) => `${CIRCLED[n] ?? n + 1} ${formatQty(xray.fills[n].qty)} @ ₹${formatPaise(xray.fills[n].price)}`).join(' · ')}</span>}
                {isStop && <span className="dx-anim-slip">✎ beyond your limit — matching stops</span>}
              </span>
            </div>
          )
        })}
        <div className="dx-num" style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'var(--ink-muted)', padding: '5px 0', borderTop: '1px solid var(--rule-strong)' }}>
          <span>{xray.type === 'MARKET' ? 'Market order: no limit' : `Your limit ₹${formatPaise(xray.limitPrice)}`}</span>
          <span>{clockTime(xray.time).slice(0, 12)}</span>
        </div>
        {rests && (
          <div style={{ display: 'grid', gridTemplateColumns: COLS, alignItems: 'center', columnGap: 12, minHeight: 40, background: 'var(--surface)', borderBottom: '1px solid var(--rule)' }}>
            <span />
            <span className="dx-num" style={{ textAlign: 'right', fontSize: 17, fontWeight: 600, color: sideColour }}>{formatPaise(xray.limitPrice)}</span>
            <span className="dx-num" style={{ textAlign: 'right', fontSize: 17 }}>{formatQty(xray.remaining)}</span>
            <span style={{ display: 'grid', placeItems: 'center' }}><Monogram bot="you" size={22} /></span>
            <span />
            <span><RubberStamp text="Rested" sub={`${formatQty(xray.remaining)} @ ${formatPaise(xray.limitPrice)}`} size="sm" angle={-5} animate slamKey={`rest-${xray.orderId}`} backed /></span>
          </div>
        )}
      </section>
      <aside style={{ flex: '1 1 320px', minWidth: 0 }}>
        <SectionLabel meta="Click a step to jump">What happened</SectionLabel>
        <ol style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {xray.steps.map((s, i) => (
            <li key={i}>
              <button type="button" onClick={() => onStep(i)} className="dx-focus" aria-current={i === step ? 'step' : undefined}
                style={{ all: 'unset', boxSizing: 'border-box', width: '100%', cursor: 'pointer', display: 'grid', gridTemplateColumns: '24px 1fr', gap: 8, padding: '10px 0', borderBottom: '1px solid var(--rule)', opacity: i <= step ? 1 : 0.4 }}>
                <span className="dx-num" style={{ fontSize: 12, color: i === step ? 'var(--accent)' : 'var(--ink-muted)', fontWeight: 600 }}>{i + 1}</span>
                <span>
                  <span style={{ display: 'block', fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 17, lineHeight: 1.2, color: i === step ? 'var(--ink)' : undefined }}>{s.title}</span>
                  {i <= step && <span style={{ display: 'block', marginTop: 3, fontSize: 13.5, lineHeight: 1.4 }}>{s.body}</span>}
                </span>
              </button>
            </li>
          ))}
        </ol>
      </aside>
    </div>
  )
}

function OrderSlip({ xray, remaining }: { xray: XRay; remaining: number }) {
  const buying = xray.side === 'BUY'
  return (
    <div style={{ maxWidth: 560, margin: '6px 0 14px', background: 'var(--surface)', border: '1px dashed var(--rule-strong)', padding: '12px 14px 10px', display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: '2px 16px', alignItems: 'baseline' }}>
      <span style={{ ...label, color: buying ? 'var(--buy)' : 'var(--sell)' }}>{buying ? '▲ Buy order' : '▼ Sell order'}</span>
      <span className="dx-num" style={{ fontSize: 11, color: 'var(--ink-muted)' }}>#{xray.orderId} · {xray.type} · {clockTime(xray.time)}</span>
      <span style={{ ...label, color: 'var(--ink-muted)' }}>Still to {buying ? 'buy' : 'sell'}</span>
      <span style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 22 }}>{buying ? 'Buy' : 'Sell'} {formatQty(xray.qty)} {xray.symbol}</span>
      <span className="dx-num" style={{ fontSize: 15 }}>{xray.type === 'MARKET' ? 'at market' : `limit ₹${formatPaise(xray.limitPrice)}`}</span>
      <span className="dx-num" style={{ fontSize: 22, fontWeight: 600, justifySelf: 'end', color: remaining ? 'var(--ink)' : 'var(--ink-muted)' }}>{formatQty(remaining)}</span>
    </div>
  )
}
