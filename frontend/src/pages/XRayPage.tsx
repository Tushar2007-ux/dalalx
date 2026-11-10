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
import { VivaTag } from '../viva/VivaTag'
import './xray.css'
import './howitworks.css'

const italic = { fontFamily: 'var(--font-display)', fontStyle: 'italic' } as const
const COLS = '44px minmax(110px,160px) minmax(130px,180px) 36px minmax(70px,140px) minmax(0,1fr)'
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
    <Shell route="xray" headline={headline} page={3} section="X-Ray">
      {!xray
        ? <EmptyXRay error={error} />
        : <>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)', alignItems: 'end', justifyContent: 'space-between', padding: '12px 0', borderBottom: '1px solid var(--rule)' }}>
            <div style={{ display: 'flex', gap: 'var(--space-4)', alignItems: 'end', flexWrap: 'wrap' }}>
              <div style={{ display: 'grid', gap: 'var(--space-1)' }}><span className="dx-label">Speed</span><OrderTypeToggle value={speed} onChange={setSpeed} options={SPEEDS} showHint={false} style={{ width: 200 }} /></div>
              <Button variant="primary" onClick={() => { if (step >= last) setStep(0); setPlaying(!playing) }}>{playing ? '❚❚ Pause' : step >= last ? '↺ Replay' : '▶ Play'}</Button>
              <Button variant="secondary" disabled={step >= last} onClick={() => { setPlaying(false); setStep((s) => Math.min(last, s + 1)) }}>Step →</Button>
              <Button variant="ghost" disabled={step === 0} onClick={() => { setPlaying(false); setStep((s) => Math.max(0, s - 1)) }}>← Back</Button>
              <span className="dx-num" style={{ fontSize: 'var(--text-ui)', color: 'var(--ink-muted)', paddingBottom: 'var(--space-2)' }}>Step {step + 1} of {xray.steps.length}</span>
            </div>
            <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'end' }}>
              {recent.length > 1 && <label style={{ display: 'grid', gap: 'var(--space-1)' }}>
                <span className="dx-label">Your recent orders</span>
                <select value={xray.orderId} onChange={(e) => choose(Number(e.target.value))} className="dx-focus"
                  style={{ fontFamily: 'var(--font-mono)', fontSize: 'var(--text-small)', padding: '6px 8px', background: 'var(--surface)', color: 'var(--ink)', border: '1px solid var(--rule-strong)', borderRadius: 0 }}>
                  {recent.map((x) => <option key={x.orderId} value={x.orderId}>#{x.orderId} · {x.side} {x.qty} {x.symbol} · {x.type}</option>)}
                </select>
              </label>}
              <a href="#/floor" style={{ ...italic, fontSize: 'var(--text-deck)', paddingBottom: 'var(--space-2)' }}>Exit X-Ray ←</a>
            </div>
          </div>
          <XRayBody xray={xray} step={step} onStep={(s) => { setPlaying(false); setStep(s) }} />
        </>}
    </Shell>
  )
}

/**
 * What the page says before you have placed an order. An empty page teaches
 * nobody anything, so it shows what an X-Ray will look like, explains what it
 * is for, and gives the two ways to get one.
 */
function EmptyXRay({ error }: { error: string | null }) {
  const offline = error !== null && error !== 'none'
  return (
    <div className="dx-xr-empty">
      <div>
        <SectionLabel rule="double">{offline ? 'The exchange is not answering' : 'Nothing to X-Ray yet'}</SectionLabel>
        <p className="dx-lead dx-prose" style={{ marginTop: 'var(--space-3)' }}>
          {offline
            ? <>DalalX cannot reach the exchange ({error}), so there is nothing to replay. Start it with <code>./run.sh</code> and this page will fill itself.</>
            : <>X-Ray replays <b>your</b> order against the book exactly as it stood the instant it arrived — every
              level it took, the price it paid at each one, and where it stopped. Place one order and come back.</>}
        </p>
        <div className="dx-xr-empty-cta">
          <a href="#/floor" className="dx-how-cta dx-focus">Place an order on the floor →</a>
          <a href="#/how" className="dx-how-cta dx-how-cta-quiet dx-focus">Try the explainer first →</a>
        </div>
      </div>
      <figure className="dx-xr-sample" aria-label="What an X-Ray looks like">
        <figcaption className="dx-label dx-muted">What you will see</figcaption>
        <div className="dx-xr-sample-row"><span className="dx-xr-sample-n">①</span><span className="dx-num">₹2,486.75</span><span className="dx-num"><s>120</s> 0</span><span>taken in full</span></div>
        <div className="dx-xr-sample-row"><span className="dx-xr-sample-n">②</span><span className="dx-num">₹2,487.00</span><span className="dx-num"><s>80</s> 0</span><span>taken in full</span></div>
        <div className="dx-xr-sample-row"><span className="dx-xr-sample-n">③</span><span className="dx-num">₹2,487.25</span><span className="dx-num"><s>200</s> 150</span><span>partly taken</span></div>
        <div className="dx-xr-sample-row is-stop"><span /><span className="dx-num">₹2,487.50</span><span /><span>beyond your limit — matching stops</span></div>
      </figure>
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
    <div style={{ display: 'flex', flexWrap: 'wrap', marginTop: 'var(--space-3)', flex: 1, gap: 'var(--space-5)' }}>
      <section style={{ flex: '2.6 1 680px', minWidth: 0 }}>
        <SectionLabel meta={`${xray.symbol} · ${buying ? 'asks' : 'bids'} when your order arrived`}>Order Book</SectionLabel>
        <div className="dx-viva-row is-left"><VivaTag note="xray" align="left" /></div>
        <OrderSlip xray={xray} remaining={view.remaining} />
        <div style={{ display: 'grid', gridTemplateColumns: COLS, columnGap: 'var(--space-3)', fontFamily: 'var(--font-mono)', fontSize: 'var(--text-label)', color: 'var(--ink-muted)', padding: '2px 0' }}>
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
          const latestFill = view.fillsShown - 1
          const justHit = latestFill >= 0 && xray.fills[latestFill]?.level === i
          return (
            <div key={level.price} className={`dx-xr-level${justHit ? ' is-hit' : ''}`} style={{ display: 'grid', gridTemplateColumns: COLS, alignItems: 'center', columnGap: 'var(--space-3)', minHeight: 52, borderBottom: isStop ? '2px dashed var(--pencil)' : '1px solid var(--rule)', background: numbers.length ? 'var(--surface)' : 'transparent' }}>
              <span key={numbers.length} className={numbers.length ? 'dx-xr-num' : undefined} style={{ fontFamily: 'var(--font-display)', fontSize: 'var(--text-title)', lineHeight: 1, color: 'var(--pencil-text)', textAlign: 'center' }}>{numbers.map((n) => CIRCLED[n] ?? n + 1).join('')}</span>
              {justHit && <span key={`chip-${latestFill}`} className="dx-xr-chip" aria-hidden="true" style={{ background: buying ? 'var(--buy)' : 'var(--sell)' }}>
                {CIRCLED[latestFill] ?? latestFill + 1} {formatQty(xray.fills[latestFill].qty)}
              </span>}
              <span className="dx-num" style={{ textAlign: 'right', fontSize: 'var(--text-num-lg)', fontWeight: 600, color: buying ? 'var(--sell)' : 'var(--buy)' }}>
                {numbers.length ? <RedPencilCircle drawKey={`f${i}`} label="Matched level" style={{ padding: '3px 12px' }}>{formatPaise(level.price)}</RedPencilCircle> : formatPaise(level.price)}
              </span>
              <span className="dx-num" style={{ textAlign: 'right', fontSize: 'var(--text-lead)', display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-2)', alignItems: 'baseline' }}>
                {taken ? <><span style={{ textDecoration: 'line-through', textDecorationColor: 'var(--pencil)', textDecorationThickness: 2, color: 'var(--ink-muted)' }}>{formatQty(total)}</span><span style={{ fontSize: 'var(--text-ui)', color: 'var(--ink-muted)' }}>→ {formatQty(left)}</span></> : formatQty(total)}
              </span>
              <span style={{ display: 'grid', placeItems: 'center' }}><Monogram bot={stampOf(mainTraderOf(level.orders) ?? '??')} size={22} /></span>
              <span aria-hidden="true" style={{ height: 16, position: 'relative', borderRight: '1px solid var(--rule)' }}>
                <span className="dx-hatch dx-depth" style={{ position: 'absolute', right: 0, top: 0, bottom: 0, width: `${(left / maxQty) * 100}%`, minWidth: left ? 2 : 0, color: buying ? 'var(--sell)' : 'var(--buy)' }} />
              </span>
              <span style={{ ...italic, fontSize: 'var(--text-body)', lineHeight: 1.25, color: 'var(--pencil-text)', minWidth: 0 }}>
                {numbers.length > 0 && <span className="dx-anim-slip">{numbers.map((n) => `${CIRCLED[n] ?? n + 1} ${formatQty(xray.fills[n].qty)} @ ₹${formatPaise(xray.fills[n].price)}`).join(' · ')}</span>}
                {isStop && <span className="dx-anim-slip">✎ beyond your limit — matching stops</span>}
              </span>
            </div>
          )
        })}
        <div className="dx-num" style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--text-small)', color: 'var(--ink-muted)', padding: '5px 0', borderTop: '1px solid var(--rule-strong)' }}>
          <span>{xray.type === 'MARKET' ? 'Market order: no limit' : `Your limit ₹${formatPaise(xray.limitPrice)}`}</span>
          <span>{clockTime(xray.time).slice(0, 12)}</span>
        </div>
        {rests && (
          <div style={{ display: 'grid', gridTemplateColumns: COLS, alignItems: 'center', columnGap: 'var(--space-3)', minHeight: 52, background: 'var(--surface)', borderBottom: '1px solid var(--rule)' }}>
            <span />
            <span className="dx-num" style={{ textAlign: 'right', fontSize: 'var(--text-lead)', fontWeight: 600, color: sideColour }}>{formatPaise(xray.limitPrice)}</span>
            <span className="dx-num" style={{ textAlign: 'right', fontSize: 'var(--text-lead)' }}>{formatQty(xray.remaining)}</span>
            <span style={{ display: 'grid', placeItems: 'center' }}><Monogram bot="you" size={22} /></span>
            <span />
            <span><RubberStamp text="Rested" sub={`${formatQty(xray.remaining)} @ ${formatPaise(xray.limitPrice)}`} size="sm" angle={-5} animate slamKey={`rest-${xray.orderId}`} backed /></span>
          </div>
        )}
      </section>
      <aside style={{ flex: '1 1 340px', minWidth: 0, position: 'sticky', top: 'var(--space-4)' }}>
        <SectionLabel meta="Click a step to jump">What happened</SectionLabel>
        <ol style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {xray.steps.map((s, i) => (
            <li key={i}>
              <button type="button" onClick={() => onStep(i)} className="dx-focus" aria-current={i === step ? 'step' : undefined}
                style={{ all: 'unset', boxSizing: 'border-box', width: '100%', cursor: 'pointer', display: 'grid', gridTemplateColumns: '24px 1fr', gap: 'var(--space-2)', padding: '10px 0', borderBottom: '1px solid var(--rule)', opacity: i <= step ? 1 : 0.4 }}>
                <span className="dx-num" style={{ fontSize: 'var(--text-small)', color: i === step ? 'var(--accent)' : 'var(--ink-muted)', fontWeight: 600 }}>{i + 1}</span>
                <span>
                  <span style={{ display: 'block', fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 'var(--text-lead)', lineHeight: 1.2, color: i === step ? 'var(--ink)' : undefined }}>{s.title}</span>
                  {i <= step && <span style={{ display: 'block', marginTop: 3, fontSize: 'var(--text-ui)', lineHeight: 1.4 }}>{s.body}</span>}
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
      <span className="dx-label" style={{ color: buying ? 'var(--buy)' : 'var(--sell)' }}>{buying ? '▲ Buy order' : '▼ Sell order'}</span>
      <span className="dx-num" style={{ fontSize: 'var(--text-label)', color: 'var(--ink-muted)' }}>#{xray.orderId} · {xray.type} · {clockTime(xray.time)}</span>
      <span className="dx-label" style={{ color: 'var(--ink-muted)' }}>Still to {buying ? 'buy' : 'sell'}</span>
      <span style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 'var(--text-num-lg)' }}>{buying ? 'Buy' : 'Sell'} {formatQty(xray.qty)} {xray.symbol}</span>
      <span className="dx-num" style={{ fontSize: 'var(--text-deck)' }}>{xray.type === 'MARKET' ? 'at market' : `limit ₹${formatPaise(xray.limitPrice)}`}</span>
      <span key={remaining} className="dx-num dx-xr-left" style={{ fontSize: 'var(--text-num-lg)', fontWeight: 600, justifySelf: 'end', color: remaining ? 'var(--ink)' : 'var(--ink-muted)' }}>{formatQty(remaining)}</span>
      <span className="dx-xr-progress" aria-hidden="true" style={{ gridColumn: '1 / -1' }}>
        <span style={{ width: `${((xray.qty - remaining) / Math.max(1, xray.qty)) * 100}%`, background: buying ? 'var(--buy)' : 'var(--sell)' }} />
      </span>
    </div>
  )
}
