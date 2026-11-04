import React from 'react';

const pad = (n) => String(n).padStart(2, '0');

export function Countdown({ seconds, endsAt, label = 'Closing bell in', warnAt = 60, finalAt = 10, size = 'md', note: noteText, style }) {
  const [now, setNow] = React.useState(Date.now());
  React.useEffect(() => {
    if (endsAt == null) return;
    const t = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(t);
  }, [endsAt]);
  const left = Math.max(0, endsAt != null ? Math.ceil((endsAt - now) / 1000) : seconds ?? 0);
  const phase = left === 0 ? 'done' : left <= finalAt ? 'final' : left <= warnAt ? 'warn' : 'normal';
  const color = { normal: 'var(--ink)', warn: 'var(--pencil-text)', final: 'var(--stamp)', done: 'var(--ink-muted)' }[phase];
  const note = noteText || { normal: 'Session in progress', warn: '✎ Final minute: no new IOC after 0:10', final: '■ Final call', done: 'Session closed' }[phase];
  const fs = size === 'lg' ? 56 : size === 'sm' ? 22 : 'var(--text-num-xl)';
  const h = Math.floor(left / 3600), m = Math.floor((left % 3600) / 60), s = left % 60;
  return (
    <div role="timer" aria-live={phase === 'final' ? 'assertive' : 'off'} style={{ display: 'grid', gap: 2, ...style }}>
      <span style={{ fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase', color: 'var(--ink-muted)' }}>{label}</span>
      {phase === 'done'
        ? <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontWeight: 600, fontSize: size === 'sm' ? 20 : 32, lineHeight: 1.1, color: 'var(--ink)' }}>The bell has rung.</span>
        : <span className="dx-num" style={{ fontSize: fs, fontWeight: 500, lineHeight: 1, color, letterSpacing: '-0.02em' }}>{h ? h + ':' : ''}{pad(m)}:{pad(s)}</span>}
      <span style={{ fontSize: 'var(--text-small)', color: phase === 'normal' || phase === 'done' ? 'var(--ink-muted)' : color }}>{note}</span>
    </div>
  );
}
