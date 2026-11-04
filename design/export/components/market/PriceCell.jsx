import React from 'react';

export function formatINR(n, decimals = 2) {
  if (n == null || isNaN(n)) return '—';
  return Number(n).toLocaleString('en-IN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}

const SIZE = { sm: 12, md: 13, lg: 22, xl: 32 };
const INK_COALESCE_MS = 250; // a cell inks at most 4×/s; faster ticks update silently

export function PriceCell({ value, change, changePct, decimals = 2, size = 'md', ink, halted = false, showChange = false, prefix = '', align = 'right', changeOnly = false, style }) {
  const prev = React.useRef(value);
  const last = React.useRef(0);
  const [mark, setMark] = React.useState({ dir: null, n: 0 });
  React.useEffect(() => {
    if (value === prev.current) return;
    const dir = value > prev.current ? 'up' : 'down';
    prev.current = value;
    const now = Date.now();
    if (now - last.current < INK_COALESCE_MS) return;
    last.current = now;
    setMark((m) => ({ dir, n: m.n + 1 }));
  }, [value]);
  const dir = ink || mark.dir;
  const cls = dir === 'up' ? 'dx-anim-ink-up' : dir === 'down' ? 'dx-anim-ink-down' : undefined;
  const fs = SIZE[size] || size;
  const c = change ?? 0;
  const chColor = c > 0 ? 'var(--buy)' : c < 0 ? 'var(--sell)' : 'var(--ink-muted)';
  const chGlyph = c > 0 ? '▲' : c < 0 ? '▼' : '·';
  return (
    <span className="dx-num" style={{ display: 'inline-flex', alignItems: 'baseline', gap: 8, justifyContent: align === 'right' ? 'flex-end' : 'flex-start', fontSize: fs, fontWeight: 500, color: 'var(--ink)', ...style }}>
      {changeOnly ? null : halted
        ? <span style={{ color: 'var(--ink-muted)', textDecoration: 'line-through' }}>{prefix}{formatINR(value, decimals)}</span>
        : <span key={ink ? 'forced-' + ink : mark.n} className={cls} style={{ padding: '0 2px', margin: '0 -2px' }}>{prefix}{formatINR(value, decimals)}</span>}
      {(showChange || changeOnly) && !halted && (
        <span style={{ fontSize: Math.max(11, Math.round(fs * 0.62)), color: chColor, whiteSpace: 'nowrap' }}>
          <span aria-hidden="true">{chGlyph} </span>
          {c === 0 ? 'Unch' : (c > 0 ? '+' : '−') + formatINR(Math.abs(c), decimals)}
          {changePct != null && c !== 0 && ' (' + (changePct > 0 ? '+' : '−') + Math.abs(changePct).toFixed(2) + '%)'}
        </span>
      )}
      {halted && <span style={{ fontFamily: 'var(--font-ui)', fontSize: 11, fontWeight: 700, letterSpacing: 'var(--tracking-label)', color: 'var(--stamp)' }}>■ HALTED</span>}
    </span>
  );
}
