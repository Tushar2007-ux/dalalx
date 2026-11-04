import { AlertNotice, Countdown, RubberStamp, SectionLabel } from '../design'
import { clockTime } from '../lib/format'
import type { MarketStatus } from '../lib/types'

const small = { fontSize: 12, color: 'var(--ink-muted)' } as const

/**
 * The band under the headline while the selected stock is halted: the
 * breaker's alert, a countdown to the reopen, and what happens next.
 */
export function HaltBand({ symbol, status }: { symbol: string; status: MarketStatus }) {
  const until = status.until ? Date.parse(status.until) : Date.now()
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(280px, 100%), 1fr))', gap: 24, padding: '12px 0 14px', borderBottom: '1px solid var(--rule-strong)', alignItems: 'start' }}>
      <AlertNotice severity="critical" label="Circuit breaker" fresh title={`Circuit breaker: trading in ${symbol} is halted`}
        time={status.since ? clockTime(status.since).slice(0, 8) : undefined}>
        {status.reason ?? 'The price moved too far, too fast.'} Your resting orders stay in the book and you can still cancel them.
      </AlertNotice>
      <Countdown endsAt={until} label="Trading resumes in" note={`Reopens at ${clockTime(until).slice(0, 8)}`} size="lg" warnAt={10} finalAt={5} />
      <div>
        <SectionLabel under="hair">What happens next</SectionLabel>
        <ol style={{ margin: 0, paddingLeft: 18, display: 'grid', gap: 5, fontSize: 13.5, lineHeight: 1.4 }}>
          <li><strong>Halt.</strong> <span style={small}>No new orders, no trades. Cancels still work.</span></li>
          <li><strong>Reopen</strong> <span className="dx-num" style={small}>{clockTime(until).slice(0, 8)}</span>. <span style={small}>The breaker starts a fresh 60-second window.</span></li>
        </ol>
      </div>
    </div>
  )
}

/** The big STOP PRESS stamp slammed across the columns during a halt. */
export function HaltStamp({ symbol }: { symbol: string }) {
  return (
    <div aria-hidden="true" style={{ position: 'absolute', left: '50%', top: 200, zIndex: 30, pointerEvents: 'none', transform: 'translateX(-50%) scale(2.2)' }}>
      <RubberStamp kind="stop-press" size="lg" angle={-9} sub={`Circuit breaker · ${symbol}`} animate slamKey={`halt-${symbol}`} />
    </div>
  )
}
