import React from 'react';

const SEV = {
  info: { glyph: '§', label: 'Market notice', level: 1, c: 'var(--ink)' },
  notice: { glyph: '¶', label: 'Exchange notice', level: 2, c: 'var(--ink)' },
  warning: { glyph: '!', label: 'Advisory', level: 3, c: 'var(--pencil-text)' },
  critical: { glyph: '‼', label: 'Surveillance action', level: 4, c: 'var(--stamp)' },
};
/* Torn top edge: a few uneven points, like a notice clipped out of the page. */
const CLIP = 'polygon(0 3px, 7% 1px, 18% 3px, 31% 0, 46% 2px, 58% 0, 72% 3px, 86% 1px, 100% 2px, 100% 100%, 0 100%)';

export function AlertNotice({ severity = 'info', label, title, children, time, reference, onDismiss, fresh = false, style }) {
  const s = SEV[severity] || SEV.info;
  const strong = severity === 'critical';
  return (
    <div role={severity === 'critical' || severity === 'warning' ? 'alert' : 'status'} className={fresh ? 'dx-anim-slip' : undefined} style={{ position: 'relative', paddingTop: 10, ...style }}>
      <span aria-hidden="true" style={{ position: 'absolute', left: 6, top: 0, fontSize: 13, lineHeight: 1, color: 'var(--ink-muted)', background: 'var(--paper)', padding: '0 3px', zIndex: 1 }}>✂</span>
      <div style={{
        clipPath: CLIP, background: 'var(--surface)', padding: '12px 14px 10px',
        border: strong ? '2px solid var(--stamp)' : '1px dashed ' + (severity === 'warning' ? 'var(--pencil)' : 'var(--rule-strong)'),
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 10 }}>
          <span style={{ fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase', color: s.c }}>
            <span aria-hidden="true" style={{ fontFamily: 'var(--font-display)', fontSize: 13, marginRight: 6 }}>{s.glyph}</span>{label || s.label}
            <span style={{ color: 'var(--ink-muted)', fontWeight: 600 }}> · Severity {s.level}/4</span>
          </span>
          {onDismiss && <button onClick={onDismiss} className="dx-focus" style={{ appearance: 'none', border: 'none', background: 'none', cursor: 'pointer', fontSize: 12, color: 'var(--ink-muted)', textDecoration: 'underline', padding: 0 }}>Dismiss</button>}
        </div>
        {title && <div style={{ marginTop: 4, fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 18, lineHeight: 1.15 }}>{title}</div>}
        {children && <div style={{ marginTop: 4, fontSize: 13, lineHeight: 1.45 }}>{children}</div>}
        {(reference || time) && <div className="dx-num" style={{ marginTop: 8, fontSize: 11, color: 'var(--ink-muted)' }}>{reference}{reference && time ? ' · ' : ''}{time}</div>}
      </div>
    </div>
  );
}
