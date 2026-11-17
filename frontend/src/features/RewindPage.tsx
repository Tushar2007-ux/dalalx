import { useCallback, useEffect, useState } from 'react'
import { Button, SectionLabel } from '../design'
import { api, ApiError } from '../lib/api'
import { clockTime, formatPaise, formatQty } from '../lib/format'
import type { JournalInfo, RewindView } from '../lib/types'
import { Shell } from '../shell/Shell'
import { useMarket } from '../store/market'
import { Spark } from '../visuals/Spark'
import { progressOf, REWIND_SPEEDS, rewindMove, sessionLabel } from './rules'
import './features.css'

const POLL_MS = 400

/**
 * Rewind: a saved trading session played back like a video.
 *
 * The server feeds the session's journal, command by command, into a second,
 * private exchange at the chosen speed; this page polls what that exchange
 * looks like and draws it. Nothing here can touch the live market.
 */
export function RewindPage() {
  const toast = useMarket((s) => s.toast)
  const [journal, setJournal] = useState<JournalInfo | null>(null)
  const [view, setView] = useState<RewindView | null>(null)
  const [speed, setSpeed] = useState(60)

  useEffect(() => {
    api.journal().then(setJournal).catch(() => {})
    api.rewind().then(setView).catch(() => {}) // one may already be playing
  }, [])

  const playing = view && (view.state === 'PLAYING' || view.state === 'PAUSED')
  // The speed buttons show the speed the tape is actually playing at.
  const playingSpeed = view?.speed
  useEffect(() => {
    if (playingSpeed) setSpeed(playingSpeed)
  }, [playingSpeed])
  useEffect(() => {
    if (!playing) return
    const timer = setInterval(() => api.rewind().then(setView).catch(() => setView(null)), POLL_MS)
    return () => clearInterval(timer)
  }, [playing])

  const fail = useCallback((what: string) => (e: unknown) =>
    toast({ kind: 'rejected', title: what, detail: e instanceof ApiError ? e.message : 'The exchange did not answer' }), [toast])

  const start = (session: string) => api.startRewind(session, speed).then(setView).catch(fail('Could not start the rewind'))
  const changeSpeed = (s: number) => {
    setSpeed(s)
    if (playing) api.rewindSpeed(s).then(setView).catch(fail('Could not change speed'))
  }

  const status = journal?.status
  return (
    <Shell route="rewind" page={10} section="Rewind" headline={{
      kicker: 'From the archive', tone: 'neutral', state: playing ? 'live' : 'stale',
      headline: 'Rewind: watch a past session again, at up to 600× speed',
      deck: 'Every command the exchange carried out is in its journal. Played back through the same engine, it produces the same trades.',
    }}>
      <div className="dx-rewind">
        <aside className="dx-rewind-shelf">
          <SectionLabel rule="double" meta={journal ? `${journal.sessions.length} on file` : undefined}>Sessions</SectionLabel>
          {status && (status.enabled
            ? <p className="dx-rewind-note">Today’s session recovered <b>{status.recoveredEntries.toLocaleString('en-IN')}</b> journal entries
                ({status.recoveredOrders.toLocaleString('en-IN')} orders) at start-up in {status.recoveryMillis} ms.</p>
            : <p className="dx-rewind-note">The journal is switched off on this server, so nothing is being saved.</p>)}
          <ul className="dx-rewind-list">
            {(journal?.sessions ?? []).map((s) => (
              <li key={s.name}>
                <button type="button" className={`dx-focus${view?.session === s.name ? ' is-on' : ''}`} onClick={() => void start(s.name)}>
                  <span>{sessionLabel(s.name)}</span>
                  <small className="dx-num">{Math.max(1, Math.round(s.bytes / 1024)).toLocaleString('en-IN')} KB · {s.name === status?.session ? 'today, still recording' : 'closed'}</small>
                </button>
              </li>
            ))}
            {journal && journal.sessions.length === 0 && <li className="dx-rewind-note">No sessions saved yet.</li>}
          </ul>
          <SectionLabel rule="hair">Speed</SectionLabel>
          <div className="dx-rewind-speeds" role="radiogroup" aria-label="Playback speed">
            {REWIND_SPEEDS.map((s) => (
              <button key={s} type="button" role="radio" aria-checked={speed === s} className="dx-focus" onClick={() => changeSpeed(s)}>{s}×</button>
            ))}
          </div>
        </aside>
        <section className="dx-rewind-screen">
          {view ? <Screen view={view} onChange={setView} fail={fail} /> : (
            <div className="dx-rewind-empty">
              <span className="dx-label">Projector off</span>
              <p>Choose a session to play it back.</p>
            </div>
          )}
        </section>
      </div>
    </Shell>
  )
}

function Screen({ view, onChange, fail }: { view: RewindView; onChange: (v: RewindView | null) => void; fail: (w: string) => (e: unknown) => void }) {
  const pct = progressOf(view)
  return (
    <>
      <div className="dx-rewind-head">
        <div>
          <span className="dx-label">{sessionLabel(view.session)}</span>
          <div className="dx-rewind-clock dx-num">{view.at ? clockTime(Date.parse(view.at)).slice(0, 8) : '--:--:--'}</div>
        </div>
        <div className="dx-rewind-controls">
          {view.state === 'PLAYING' && <Button size="sm" variant="secondary" onClick={() => api.pauseRewind().then(onChange).catch(fail('Could not pause'))}>❚❚ Pause</Button>}
          {view.state === 'PAUSED' && <Button size="sm" variant="primary" onClick={() => api.resumeRewind().then(onChange).catch(fail('Could not resume'))}>▶ Play</Button>}
          <Button size="sm" variant="ghost" onClick={() => api.stopRewind().then(() => onChange(null)).catch(fail('Could not stop'))}>■ Stop</Button>
        </div>
      </div>
      <div className="dx-rewind-progress" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label="Played">
        <span style={{ width: `${pct}%` }} />
      </div>
      <p className="dx-rewind-note dx-num">{view.done.toLocaleString('en-IN')} of {view.total.toLocaleString('en-IN')} commands · {view.speed}× · {view.state === 'FINISHED' ? 'the end of the tape' : view.state.toLowerCase()}</p>
      <div className="dx-rewind-stocks">
        {view.stocks.map((s) => {
          const move = rewindMove(s.open, s.last)
          return (
            <article key={s.symbol} className={`dx-rewind-stock${s.phase !== 'OPEN' ? ' is-halted' : ''}`}>
              <header><b>{s.symbol}</b><span className={`dx-num ${move > 0 ? 'is-up' : move < 0 ? 'is-down' : ''}`}>{move > 0 ? '▲' : move < 0 ? '▼' : '·'} {Math.abs(move).toFixed(2)}%</span></header>
              <div className="dx-rewind-price dx-num">{s.last ? `₹${formatPaise(s.last)}` : 'no trades yet'}</div>
              <Spark values={s.trail} width={180} height={34} />
              <footer className="dx-num">H ₹{formatPaise(s.high)} · L ₹{formatPaise(s.low)} · {formatQty(s.volume)} sh{s.phase !== 'OPEN' ? ` · ${s.phase}` : ''}</footer>
            </article>
          )
        })}
      </div>
      <SectionLabel rule="hair" meta="Newest first">The tape, as it was</SectionLabel>
      <ol className="dx-rewind-tape dx-num">
        {view.tape.map((t, i) => (
          <li key={`${t.at}-${i}`}>
            <span>{clockTime(Date.parse(t.at)).slice(0, 8)}</span><b>{t.symbol}</b><span>{formatQty(t.qty)} @ ₹{formatPaise(t.price)}</span><span className="dx-rewind-who">{t.buyerId} ← {t.sellerId}</span>
          </li>
        ))}
      </ol>
    </>
  )
}
