import { useEffect, useState, type ReactNode } from 'react'
import {
  AlertNotice, Button, MarginNote, Monogram, Portrait, RubberStamp, SectionLabel,
} from '../design'
import { api, ApiError } from '../lib/api'
import { clockTime, formatPaise, hhmm } from '../lib/format'
import { botKey } from '../lib/market'
import { monogramOf } from '../lib/monogram'
import type { Case, RuleInfo } from '../lib/types'
import { Shell } from '../shell/Shell'
import { useMarket } from '../store/market'
import type { Headline } from '../terminal/headline'
import { timelineOf } from './timeline'

const label = { fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase' } as const
const SEVERITY = { CRITICAL: 'critical', WARNING: 'warning', NOTICE: 'notice' } as const
const NAMES: Record<string, string> = { MUNIM: 'The Munim', MOMENTUM: 'Momentum Chaser', PANIC: 'Panic Seller', WHALE: 'The Whale', SPOOFER: 'The Spoofer', YOU: 'You' }

const nameOf = (id: string) => NAMES[id] ?? id
const stampOf = (id: string) => botKey(id) ?? monogramOf(id)

/**
 * "Investigations": the Surveillance Inspector's desk. An alert feed of cases,
 * the selected case file (figures, evidence timeline, pencil notes, ruling
 * buttons), and the rules with their switches. Layout follows
 * design/export/ui_kits/surveillance.
 */
export function Investigations() {
  const cases = useMarket((s) => s.cases)
  const applyCase = useMarket((s) => s.applyCase)
  const toast = useMarket((s) => s.toast)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [rules, setRules] = useState<RuleInfo[]>([])
  const [suspended, setSuspended] = useState<string[]>([])

  useEffect(() => {
    api.rules().then(setRules).catch(() => {})
    api.suspended().then(setSuspended).catch(() => {})
    api.cases().then(useMarket.getState().setCases).catch(() => {})
  }, [])

  const selected = cases.find((c) => c.id === selectedId) ?? cases[0]
  const open = cases.filter((c) => c.status === 'OPEN').length
  const fail = (what: string) => (e: unknown) =>
    toast({ kind: 'rejected', title: what, detail: e instanceof ApiError ? e.message : 'The exchange did not answer' })

  const rule = (action: 'dismiss' | 'suspend') => {
    if (!selected) return
    const call = action === 'dismiss' ? api.dismissCase(selected.id) : api.suspendCase(selected.id)
    call.then((c) => {
      applyCase(c)
      if (action === 'suspend') api.suspended().then(setSuspended).catch(() => {})
    }).catch(fail(`Could not ${action} ${selected.id}`))
  }
  const reinstate = (trader: string) =>
    api.reinstate(trader).then(setSuspended).then(() => toast({ kind: 'info', title: `${nameOf(trader)} reinstated` })).catch(fail('Could not reinstate'))

  const headline: Headline = selected?.status === 'SUSPENDED'
    ? { kicker: 'Stop Press', state: 'breaking', tone: 'down', headline: `${nameOf(selected.traderId)} suspended; all resting orders cancelled` }
    : selected
      ? { kicker: 'Investigations', state: selected.status === 'OPEN' ? 'live' : 'stale', tone: 'neutral', headline: `Surveillance desk opens a ${selected.ruleName.toLowerCase()} case against ${nameOf(selected.traderId)}`, deck: selected.summary }
      : { kicker: 'Investigations', state: 'live', tone: 'neutral', headline: 'The Surveillance Inspector is watching every order', deck: 'Spoofing, layering, wash trades and pumps are flagged here the moment they happen.' }

  return (
    <Shell route="surveillance" headline={headline} page={3} section="Investigations">
      <header style={{ display: 'flex', alignItems: 'center', gap: 16, margin: '16px 0 12px' }}>
        <Portrait bot="inspector" size={76} />
        <div>
          <h1 style={{ margin: 0, fontFamily: 'var(--font-display)', fontWeight: 800, fontSize: 40, lineHeight: 1, letterSpacing: '-0.01em' }}>Investigations</h1>
          <div style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 15, marginTop: 4 }}>
            Filed by the Surveillance Inspector · <span className="dx-num" style={{ fontStyle: 'normal', fontSize: 13 }}>{open}</span> open {open === 1 ? 'case' : 'cases'}
          </div>
        </div>
      </header>

      <div style={{ display: 'flex', flexWrap: 'wrap', rowGap: 24 }}>
        <section style={{ flex: '1 1 320px', minWidth: 0, paddingRight: 14 }}>
          <SectionLabel count={cases.length} meta="Newest first">Alert Feed</SectionLabel>
          {cases.length === 0 && <p style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', color: 'var(--ink-muted)' }}>All quiet. The Inspector reads every line of the tape; cases appear here the moment a rule trips.</p>}
          <div style={{ display: 'grid', gap: 10, marginTop: 6, maxHeight: 640, overflowY: 'auto', paddingRight: 4 }}>
            {cases.map((c) => (
              <div key={c.id} role="button" tabIndex={0} aria-pressed={c.id === selected?.id} onClick={() => setSelectedId(c.id)}
                onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && setSelectedId(c.id)} className="dx-focus"
                style={{ cursor: 'pointer', outline: c.id === selected?.id ? '2px solid var(--rule-strong)' : 'none', outlineOffset: 3, opacity: c.status === 'DISMISSED' ? 0.6 : 1 }}>
                <AlertNotice severity={c.status === 'OPEN' ? SEVERITY[c.severity] : 'info'} title={`${c.ruleName} · ${c.symbol}`}
                  reference={c.id + (c.status !== 'OPEN' ? ` · ${c.status === 'SUSPENDED' ? 'Suspended' : 'Dismissed'}` : '')} time={clockTime(c.openedAt).slice(0, 8)}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 2 }}>
                    <Monogram bot={stampOf(c.traderId)} size={24} />
                    <span style={{ fontWeight: 600 }}>{nameOf(c.traderId)}</span>
                  </div>
                  <div className="dx-num" style={{ fontSize: 12, marginTop: 5 }}>{c.summary}</div>
                </AlertNotice>
              </div>
            ))}
          </div>
        </section>

        <section style={{ flex: '2.4 1 600px', minWidth: 0, borderLeft: '1px solid var(--rule)', padding: '0 14px' }}>
          {selected ? <CaseFile c={selected} onRule={rule} /> : <SectionLabel>Case File</SectionLabel>}
        </section>
      </div>

      <div style={{ marginTop: 28 }}>
        <SectionLabel rule="double" meta="What the Inspector looks for">Rules</SectionLabel>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(260px, 100%), 1fr))', gap: '12px 24px', marginTop: 8 }}>
          {rules.map((r) => (
            <div key={r.id} style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 8, alignItems: 'center', borderTop: '1px solid var(--rule)', paddingTop: 8 }}>
              <div>
                <div style={{ fontWeight: 700 }}>{r.name}</div>
                <div className="dx-num" style={{ fontSize: 12, color: 'var(--ink-muted)' }}>{r.threshold}</div>
              </div>
              <RuleSwitch on={r.enabled} label={r.name} onChange={(on) => api.setRule(r.id, on).then(setRules).catch(fail('Could not change the rule'))} />
            </div>
          ))}
        </div>
        {suspended.length > 0 && <div style={{ marginTop: 18 }}>
          <SectionLabel under="hair">Suspended traders</SectionLabel>
          {suspended.map((t) => (
            <div key={t} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 0' }}>
              <Monogram bot={stampOf(t)} size={24} /><span style={{ fontWeight: 600 }}>{nameOf(t)}</span>
              <Button size="sm" variant="secondary" onClick={() => void reinstate(t)}>Reinstate</Button>
            </div>
          ))}
        </div>}
      </div>
    </Shell>
  )
}

function RuleSwitch({ on, onChange, label: name }: { on: boolean; onChange: (on: boolean) => void; label: string }) {
  return (
    <button type="button" role="switch" aria-checked={on} aria-label={name} onClick={() => onChange(!on)} className="dx-focus"
      style={{ appearance: 'none', borderRadius: 0, cursor: 'pointer', display: 'grid', gridTemplateColumns: '1fr 1fr', border: '1px solid var(--rule-strong)', padding: 0, height: 24, width: 74, background: 'transparent', fontFamily: 'var(--font-ui)', fontSize: 11, fontWeight: 700, letterSpacing: '0.08em' }}>
      <span style={{ display: 'grid', placeItems: 'center', background: on ? 'var(--ink)' : 'transparent', color: on ? 'var(--ink-inverse)' : 'var(--ink-muted)' }}>ON</span>
      <span style={{ display: 'grid', placeItems: 'center', background: on ? 'transparent' : 'var(--ink)', color: on ? 'var(--ink-muted)' : 'var(--ink-inverse)' }}>OFF</span>
    </button>
  )
}

function CaseFile({ c, onRule }: { c: Case; onRule: (action: 'dismiss' | 'suspend') => void }) {
  const stamp = c.status === 'SUSPENDED' ? 'Suspended' : c.status === 'DISMISSED' ? 'Dismissed' : 'Under Investigation'
  return (
    <>
      <SectionLabel meta={`${c.id} · last 30 seconds`}>Case File</SectionLabel>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', gap: 16, margin: '6px 0 10px' }}>
        <div>
          <div style={{ ...label, color: c.status === 'OPEN' ? 'var(--stamp)' : 'var(--ink-muted)' }}>{c.ruleName} · {c.symbol}</div>
          <h2 style={{ margin: '3px 0 0', fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 28, lineHeight: 1.1 }}>{nameOf(c.traderId)}: {c.summary.split(' · ')[0]}</h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}><Monogram bot={stampOf(c.traderId)} size={28} /><span style={{ fontSize: 13 }}>Trader {c.traderId} · opened {clockTime(c.openedAt).slice(0, 8)}</span></div>
        </div>
        <RubberStamp key={c.id + c.status} kind="under-investigation" text={stamp} color={c.status === 'DISMISSED' ? 'var(--ink-muted)' : undefined} sub={c.id} size="md" angle={-6} animate backed />
      </div>
      <dl style={{ margin: '0 0 12px', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: 12 }}>
        {Object.entries(c.figures).map(([k, v]) => (
          <div key={k} style={{ borderTop: '1px solid var(--rule-strong)', paddingTop: 5 }}>
            <dt style={{ ...label, fontSize: 10, fontWeight: 600, color: 'var(--ink-muted)' }}>{k}</dt>
            <dd className="dx-num" style={{ margin: '2px 0 0', fontSize: 22, fontWeight: 500 }}>{v}</dd>
          </div>
        ))}
      </dl>
      <div style={{ border: '1px solid var(--rule)', background: 'var(--surface)', padding: '10px 12px 8px' }}><EvidenceTimeline c={c} /></div>
      <Legend />
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) auto', gap: 16, alignItems: 'end', marginTop: 14, borderTop: '3px double var(--rule-strong)', paddingTop: 10 }}>
        <MarginNote author="Inspector" time={hhmm(new Date(c.openedAt))} side="below">
          {c.status === 'SUSPENDED' ? 'Suspended. Resting orders cancelled on every stock.' : c.status === 'DISMISSED' ? 'Dismissed after review. No further action.' : c.notes.join(' ')}
        </MarginNote>
        <div style={{ display: 'flex', gap: 8 }}>
          {c.status === 'OPEN' && <>
            <Button variant="secondary" onClick={() => onRule('dismiss')}>Dismiss</Button>
            <Button variant="danger" onClick={() => onRule('suspend')}>Suspend {nameOf(c.traderId)}</Button>
          </>}
        </div>
      </div>
    </>
  )
}

function EvidenceTimeline({ c }: { c: Case }) {
  const t = timelineOf(c)
  const W = 760, H = 320, L = 8, R = 78, T = 48, B = 30
  const pad = Math.max(10, (t.high - t.low) * 0.15)
  const lo = t.low - pad, hi = t.high + pad
  const X = (f: number) => L + f * (W - L - R)
  const Y = (p: number) => T + (1 - (p - lo) / (hi - lo)) * (H - T - B)
  const levels = [...new Set(t.marks.map((m) => m.price))].sort((a, b) => a - b)
  const every = Math.max(1, Math.ceil(levels.length / 6))
  const flagged = t.marks.filter((m) => m.flagged)
  const ring = flagged.length ? {
    x0: X(Math.min(...flagged.map((m) => m.x0))) - 10, x1: X(Math.max(...flagged.map((m) => m.x1))) + 10,
    y0: Math.min(...flagged.map((m) => Y(m.price))) - 12, y1: Math.max(...flagged.map((m) => Y(m.price))) + 12,
  } : null
  return (
    <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`Evidence timeline for ${c.id}: ${t.marks.length} orders in the last 30 seconds`} style={{ width: '100%', height: 'auto', display: 'block', overflow: 'visible' }}>
      {levels.filter((_, i) => i % every === 0).map((p) => <g key={p}><line x1={L} x2={W - R} y1={Y(p)} y2={Y(p)} stroke="var(--rule)" /><text x={W - R + 6} y={Y(p) + 3.5} fontFamily="var(--font-mono)" fontSize="10.5" fill="var(--ink-muted)">{formatPaise(p)}</text></g>)}
      {[-30, -25, -20, -15, -10, -5, 0].map((s) => <g key={s}><line x1={X((s + 30) / 30)} x2={X((s + 30) / 30)} y1={T} y2={H - B} stroke="var(--rule)" strokeDasharray="1 3" /><text x={X((s + 30) / 30)} y={H - B + 16} textAnchor="middle" fontFamily="var(--font-mono)" fontSize="10.5" fill={s === 0 ? 'var(--ink)' : 'var(--ink-muted)'}>{s === 0 ? clockTime(c.openedAt).slice(0, 8) : `${s}s`}</text></g>)}
      <line x1={L} x2={W - R} y1={H - B} y2={H - B} stroke="var(--rule-strong)" />
      {t.marks.map((m) => {
        const col = m.side === 'bid' ? 'var(--buy)' : 'var(--sell)'
        const y = Y(m.price), x0 = X(m.x0), x1 = Math.max(X(m.x1), x0 + 3)
        return (
          <g key={m.id}>
            <line x1={x0} x2={x1} y1={y} y2={y} stroke={col} strokeWidth={Math.max(1.5, Math.min(6, m.qty / 2000))} />
            <rect x={x0 - 3} y={y - 3} width="6" height="6" fill="var(--paper)" stroke={col} strokeWidth="1.2" />
            {m.outcome === 'CANCELLED' ? <path d={`M ${x1 - 3} ${y - 3} l 6 6 M ${x1 + 3} ${y - 3} l -6 6`} stroke={col} strokeWidth="1.5" />
              : m.outcome === 'FILLED' ? <circle cx={x1} cy={y} r="3.2" fill={col} /> : null}
          </g>
        )
      })}
      {ring && <ellipse key={c.id} cx={(ring.x0 + ring.x1) / 2} cy={(ring.y0 + ring.y1) / 2} rx={(ring.x1 - ring.x0) / 2} ry={(ring.y1 - ring.y0) / 2}
        fill="none" stroke="var(--pencil)" strokeWidth="2" pathLength={100} strokeDasharray="100" strokeDashoffset="100" className="dx-anim-draw" />}
      {ring && c.notes.map((note, i) => (
        <text key={i} x={W - R - 4} y={14 + i * 18} textAnchor="end" fontFamily="var(--font-display)" fontStyle="italic" fontSize="14" fill="var(--pencil-text)">{i === 0 ? '①' : '②'} {note}</text>
      ))}
      {t.marks.length === 0 && <text x={W / 2} y={H / 2} textAnchor="middle" fontFamily="var(--font-display)" fontStyle="italic" fill="var(--ink-muted)">No priced orders in the window.</text>}
    </svg>
  )
}

function Legend() {
  const item = (mark: ReactNode, text: string) => <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>{mark}<span>{text}</span></span>
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, fontSize: 12, color: 'var(--ink-muted)', marginTop: 8 }}>
      {item(<svg width="10" height="10"><rect x="2" y="2" width="6" height="6" fill="none" stroke="var(--ink)" /></svg>, 'Placed')}
      {item(<span style={{ color: 'var(--ink)' }}>✕</span>, 'Cancelled')}
      {item(<svg width="10" height="10"><circle cx="5" cy="5" r="3.2" fill="var(--ink)" /></svg>, 'Filled')}
      {item(<span style={{ color: 'var(--buy)', fontWeight: 600 }}>▲ Bid</span>, '')}
      {item(<span style={{ color: 'var(--sell)', fontWeight: 600 }}>▼ Ask</span>, '')}
      {item(<span style={{ width: 22, borderTop: '5px solid var(--ink)' }} />, 'Thickness = size, length = time alive')}
    </div>
  )
}
