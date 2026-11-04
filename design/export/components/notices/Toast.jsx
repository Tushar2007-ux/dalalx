import React from 'react';

const KIND = {
  filled: { glyph: '✓', label: 'Filled', c: 'var(--buy)' },
  partial: { glyph: '◐', label: 'Partly filled', c: 'var(--ink)' },
  rejected: { glyph: '✕', label: 'Rejected', c: 'var(--stamp)' },
  cancelled: { glyph: '—', label: 'Cancelled', c: 'var(--ink-muted)' },
  info: { glyph: '§', label: 'Notice', c: 'var(--ink)' },
};

export function Toast({ kind = 'filled', title, detail, time, action, onClose, animate = true, style }) {
  const k = KIND[kind] || KIND.info;
  return (
    <div role={kind === 'rejected' ? 'alert' : 'status'} className={animate ? 'dx-anim-slip' : undefined}
      style={{ width: 340, maxWidth: '100%', boxSizing: 'border-box', background: 'var(--surface)', border: '1px solid var(--rule-strong)', borderTop: '3px solid ' + k.c, padding: '9px 12px 10px', ...style }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 8 }}>
        <span style={{ fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase', color: k.c }}>
          <span aria-hidden="true">{k.glyph}</span> {k.label}
        </span>
        <span style={{ display: 'flex', gap: 10, alignItems: 'baseline' }}>
          {time && <span className="dx-num" style={{ fontSize: 11, color: 'var(--ink-muted)' }}>{time}</span>}
          {onClose && <button aria-label="Close" onClick={onClose} className="dx-focus" style={{ appearance: 'none', border: 'none', background: 'none', cursor: 'pointer', color: 'var(--ink-muted)', fontSize: 14, padding: 0, lineHeight: 1 }}>✕</button>}
        </span>
      </div>
      <div style={{ marginTop: 3, fontFamily: 'var(--font-display)', fontWeight: 600, fontSize: 16, lineHeight: 1.2 }}>{title}</div>
      {detail && <div className="dx-num" style={{ marginTop: 3, fontSize: 12, color: 'var(--ink-muted)' }}>{detail}</div>}
      {action && <button onClick={action.onClick} className="dx-focus" style={{ marginTop: 6, appearance: 'none', border: 'none', background: 'none', padding: 0, cursor: 'pointer', fontFamily: 'var(--font-ui)', fontSize: 12, fontWeight: 600, color: 'var(--ink)', textDecoration: 'underline', textUnderlineOffset: 2 }}>{action.label}</button>}
    </div>
  );
}
