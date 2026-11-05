import { DEMO } from './script'
import { useDemo } from './autopilot'

const button = { appearance: 'none', border: '1px solid var(--ink-inverse)', background: 'transparent', color: 'var(--ink-inverse)', fontFamily: 'var(--font-ui)', fontSize: 12, fontWeight: 600, padding: '4px 10px', cursor: 'pointer', borderRadius: 0 } as const

/** The caption strip along the bottom while the autopilot runs, with pause, skip and stop. */
export function DemoBar() {
  const { running, paused, step, elapsed, togglePause, next, stop } = useDemo()
  if (!running) return null
  const current = DEMO[step]
  const progress = Math.min(1, elapsed / (current.until ? current.maxSeconds ?? current.seconds : current.seconds))
  return (
    <div role="region" aria-label="Demo autopilot" aria-live="polite"
      style={{ position: 'fixed', left: 0, right: 0, bottom: 0, zIndex: 50, background: 'var(--ink)', color: 'var(--ink-inverse)', padding: '10px 24px 12px', boxShadow: '0 -1px 0 var(--rule-strong)' }}>
      <div style={{ maxWidth: 1320, margin: '0 auto', display: 'grid', gridTemplateColumns: 'minmax(0,1fr) auto', gap: 16, alignItems: 'center' }}>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontFamily: 'var(--font-ui)', fontSize: 11, fontWeight: 700, letterSpacing: '0.14em', textTransform: 'uppercase', opacity: 0.85 }}>
            ▶ Demo · step {step + 1} of {DEMO.length} · {current.title}{paused ? ' · paused' : ''}
          </div>
          <div style={{ fontFamily: 'var(--font-display)', fontSize: 18, lineHeight: 1.3, marginTop: 2 }}>{current.caption}</div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button type="button" style={button} onClick={togglePause}>{paused ? '▶ Resume' : '❚❚ Pause'}</button>
          <button type="button" style={button} onClick={next}>Skip →</button>
          <button type="button" style={button} onClick={stop}>✕ Stop</button>
        </div>
      </div>
      <div aria-hidden="true" style={{ position: 'absolute', left: 0, top: 0, height: 3, width: `${progress * 100}%`, background: 'var(--accent)', transition: 'width 250ms linear' }} />
    </div>
  )
}
