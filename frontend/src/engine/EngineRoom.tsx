import { useEffect, useRef, useState } from 'react'
import { SectionLabel } from '../design'
import { api } from '../lib/api'
import { COMMAND_SOURCES, type CommandSource, type RushResult, type WorkerLoad } from '../lib/types'
import { Shell } from '../shell/Shell'
import { useMarket } from '../store/market'
import { SOURCE_STYLE, arrivalRate, compact, queueFill, threadMood, ticketScale, ticketsFor } from './factory'
import './engine.css'

/** How long a ticket takes to ride the belt to the press. */
const BELT_MS = 1700
/** How often the lanes decide which tickets to print. */
const SLICE_MS = 100

/**
 * The Engine Room: the exchange's concurrency, drawn as a print works.
 *
 * Each stock is a lane. Orders and other commands arrive as paper tickets
 * (coloured by who sent them), ride a conveyor belt into the lane's queue
 * bin (the worker's {@code BlockingQueue}) and are pressed one at a time by
 * the lane's single worker thread. Everything on screen is measured from the
 * real threads about four times a second (GET /api/engine and /topic/engine).
 *
 * The Rush Hour button makes eight producer threads fire 40,000 commands at
 * once, so you can watch the queues fill and drain.
 */
export function EngineRoom() {
  const engine = useMarket((s) => s.engine)
  const applyEngine = useMarket((s) => s.applyEngine)
  const connection = useMarket((s) => s.connection)
  const [rush, setRush] = useState<{ state: 'idle' | 'running' | 'done' | 'error'; result?: RushResult; error?: string }>({ state: 'idle' })
  const [peak, setPeak] = useState<Record<string, number>>({})

  useEffect(() => {
    api.engine().then(applyEngine).catch(() => {})
  }, [applyEngine])

  // Remember each queue's highest point for a few seconds, so a rush that lasts
  // a fraction of a second is still visible.
  useEffect(() => {
    if (!engine) return
    setPeak((p) => {
      const next: Record<string, number> = {}
      for (const w of engine.workers) next[w.symbol] = Math.max(w.pending, (p[w.symbol] ?? 0) * 0.82)
      return next
    })
  }, [engine])

  const workers = engine?.workers ?? []
  const total = workers.reduce((a, w) => a + w.perSecond, 0)
  const busy = workers.filter((w) => threadMood(w) === 'Pressing').length
  const totalProcessed = workers.reduce((a, w) => a + w.processed, 0)

  const startRush = async () => {
    setRush({ state: 'running' })
    try {
      setRush({ state: 'done', result: await api.rush() })
    } catch (e) {
      setRush({ state: 'error', error: e instanceof Error ? e.message : 'The rush could not start' })
    }
  }

  return (
    <Shell route="engine" page={6} section="Engine Room" headline={{
      kicker: 'Inside the exchange', tone: 'neutral', state: connection === 'offline' ? 'stale' : 'live',
      headline: 'Five presses, one thread each: how DalalX matches without a single lock',
      deck: 'Every order is a ticket on a belt. Each stock’s worker thread takes them one at a time from its own queue, so no two threads ever touch the same book.',
    }}>
      <section className="dx-engine-top">
        <div className="dx-engine-stats">
          <Stat label="Commands / second" value={compact(total)} />
          <Stat label="Presses running" value={`${busy} / ${workers.length || 5}`} />
          <Stat label="Pressed since opening" value={compact(totalProcessed)} />
        </div>
        <div className="dx-engine-rush">
          <button type="button" className="dx-engine-rush-btn dx-focus" onClick={startRush} disabled={rush.state === 'running' || connection !== 'live'}>
            {rush.state === 'running' ? 'Rush hour…' : 'Start Rush Hour'}
          </button>
          <span className="dx-engine-rush-note">
            {rush.state === 'done' && rush.result
              ? <><b>{rush.result.producers} producer threads</b> sent <b>{rush.result.commands.toLocaleString('en-IN')}</b> commands; all pressed in <b>{(rush.result.millis / 1000).toFixed(2)} s</b> ({compact(rush.result.commands / Math.max(rush.result.millis, 1) * 1000)}/s).</>
              : rush.state === 'error' ? rush.error
                : '8 threads fire 40,000 commands at the five queues at once. The market itself is untouched.'}
          </span>
        </div>
      </section>

      <div className="dx-engine-legend" aria-label="Ticket colours">
        {COMMAND_SOURCES.map((s) => (
          <span key={s} title={SOURCE_STYLE[s].who}><i className="dx-ticket" style={{ background: SOURCE_STYLE[s].colour }} />{SOURCE_STYLE[s].label}</span>
        ))}
      </div>

      <section className="dx-factory" aria-label="Worker threads">
        {workers.length === 0 && <p className="dx-engine-empty">Waiting for the first reading from the presses…</p>}
        {workers.map((w) => <Lane key={w.symbol} worker={w} peak={peak[w.symbol] ?? w.pending} />)}
      </section>

      <section className="dx-engine-explain">
        <Explainer title="Producer–consumer" code="SymbolWorker.enqueue / runLoop">
          Many threads put tickets on a belt (the web server, the bot thread, the clock, phones in the room). Exactly one thread per stock takes them off, with <code>BlockingQueue.take()</code>, which sleeps while the bin is empty: no busy-waiting.
        </Explainer>
        <Explainer title="Thread confinement" code="OrderBook (no locks)">
          Only <code>worker-RELIANCE</code> ever touches the RELIANCE book. Nothing is shared, so nothing needs a lock, and orders are matched in exactly the order they arrived. The five presses run in parallel on different CPU cores.
        </Explainer>
        <Explainer title="Back-pressure" code="queue.offer(cmd, 1 s)">
          Each bin holds up to 100,000 tickets. If it is ever full, a producer waits up to a second for room, then the order is refused with “too busy” (HTTP 503) instead of freezing the caller.
        </Explainer>
      </section>
    </Shell>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="dx-engine-stat">
      <span className="dx-num">{value}</span>
      <small>{label}</small>
    </div>
  )
}

function Explainer({ title, code, children }: { title: string; code: string; children: React.ReactNode }) {
  return (
    <article>
      <SectionLabel rule="bold" under="hair" meta={<code>{code}</code>}>{title}</SectionLabel>
      <p>{children}</p>
    </article>
  )
}

interface Ticket {
  id: number
  source: CommandSource
  lane: number
}

let nextTicket = 1

/** One stock: belt → queue bin → press → output tray. */
function Lane({ worker, peak }: { worker: WorkerLoad; peak: number }) {
  const rate = arrivalRate(worker)
  const scale = ticketScale(rate)
  const mood = threadMood(worker)
  const pressing = mood === 'Pressing'
  // The ram strokes whenever the thread is doing work at all, faster when busier.
  const working = pressing || worker.perSecond > 0
  // Faster stamping when the worker is busier (between 0.18 s and 1.1 s per stroke).
  const strokeMs = Math.max(180, Math.min(1100, 1100 - Math.log10(worker.perSecond + 1) * 260))
  const fill = queueFill(Math.max(worker.pending, peak), worker.capacity)

  return (
    <div className={`dx-lane${working ? ' is-working' : ''}${pressing ? ' is-pressing' : ''}`}>
      <div className="dx-lane-head">
        <strong>{worker.symbol}</strong>
        <span className="dx-num">{compact(rate)} in/s</span>
        {scale > 1 && <span className="dx-lane-scale">1 ticket ≈ {scale.toLocaleString('en-IN')}</span>}
      </div>
      <Belt rates={worker.arrivalsPerSecond} scale={scale} speed={rate} />
      <div className="dx-bin" title={`${worker.pending.toLocaleString('en-IN')} waiting · capacity ${worker.capacity.toLocaleString('en-IN')}`}>
        <div className="dx-bin-fill" style={{ height: `${fill * 100}%` }} />
        <span className="dx-bin-label dx-num">{worker.pending.toLocaleString('en-IN')}</span>
        <small>{peak > worker.pending + 1 ? `peak ${compact(peak)}` : 'waiting'}</small>
      </div>
      <div className="dx-press" style={{ ['--stroke' as string]: `${strokeMs}ms` }}>
        <div className="dx-press-ram" aria-hidden="true" />
        <div className="dx-press-bed" aria-hidden="true" />
        <div className="dx-press-name dx-num">{worker.thread}</div>
        <div className={`dx-press-mood${pressing ? ' is-on' : ''}`}>{mood}</div>
        <div className="dx-press-meter" title="Share of time spent pressing">
          <span style={{ width: `${Math.min(100, Math.max(worker.utilisation * 100, pressing ? 2 : 0))}%` }} />
        </div>
        <div className="dx-press-rate dx-num">{compact(worker.perSecond)}/s · {(worker.utilisation * 100).toFixed(worker.utilisation < 0.1 ? 1 : 0)}% busy</div>
      </div>
      <div className="dx-tray" aria-hidden="true">
        <div className="dx-tray-sheet" />
        <div className="dx-tray-sheet" />
        <div className="dx-tray-sheet" />
        <span className="dx-num">{compact(worker.processed)}</span>
        <small>pressed</small>
      </div>
    </div>
  )
}

/** The conveyor belt: prints tickets at the measured rates and sends them along to the queue. */
function Belt({ rates, scale, speed }: { rates: WorkerLoad['arrivalsPerSecond']; scale: number; speed: number }) {
  const [tickets, setTickets] = useState<Ticket[]>([])
  const latest = useRef({ rates, scale })
  const carry = useRef<Partial<Record<CommandSource, number>>>({})
  latest.current = { rates, scale }

  useEffect(() => {
    const timer = setInterval(() => {
      if (document.hidden) return
      const due = ticketsFor(latest.current.rates, SLICE_MS / 1000, carry.current, latest.current.scale)
      if (!due.length) return
      setTickets((t) => [...t, ...due.map((source) => ({ id: nextTicket++, source, lane: Math.floor(Math.random() * 3) }))].slice(-90))
    }, SLICE_MS)
    return () => clearInterval(timer)
  }, [])

  const drop = (id: number) => setTickets((t) => t.filter((x) => x.id !== id))
  // The belt's surface moves faster when there is more traffic.
  const beltSeconds = Math.max(0.35, 2.4 - Math.log10(speed + 1) * 0.6)

  return (
    <div className="dx-belt" style={{ ['--belt' as string]: `${beltSeconds}s` }}>
      <div className="dx-belt-surface" aria-hidden="true" />
      {tickets.map((t) => (
        <i key={t.id} className="dx-ticket dx-ticket-ride" onAnimationEnd={() => drop(t.id)}
          style={{ background: SOURCE_STYLE[t.source].colour, top: `${18 + t.lane * 11}%`, animationDuration: `${BELT_MS}ms` }} />
      ))}
    </div>
  )
}
