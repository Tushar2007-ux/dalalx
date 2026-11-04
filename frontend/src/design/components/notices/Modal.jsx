import React from 'react';

export function Modal({ open = true, kicker, title, children, actions, onClose, width = 480, inline = false, style }) {
  React.useEffect(() => {
    if (!open || !onClose) return;
    const k = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', k);
    return () => window.removeEventListener('keydown', k);
  }, [open, onClose]);
  if (!open) return null;
  const titleId = 'dx-modal-' + String(title || 'dialog').toLowerCase().replace(/\W+/g, '-');
  return (
    <div className="dx-anim-fade" onClick={onClose}
      style={{ position: inline ? 'relative' : 'fixed', inset: inline ? undefined : 0, zIndex: 50, background: 'var(--scrim)', display: 'grid', placeItems: 'center', padding: 24 }}>
      <div role="dialog" aria-modal="true" aria-labelledby={titleId} onClick={(e) => e.stopPropagation()}
        style={{ width, maxWidth: '100%', boxSizing: 'border-box', background: 'var(--surface)', color: 'var(--ink)', border: '1px solid var(--rule-strong)', borderTop: '4px double var(--rule-strong)', ...style }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', gap: 12, padding: '14px 18px 10px', borderBottom: '1px solid var(--rule)' }}>
          <div>
            {kicker && <div style={{ fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase', color: 'var(--accent)' }}>{kicker}</div>}
            <h2 id={titleId} style={{ margin: '2px 0 0', fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 24, lineHeight: 1.1, fontVariationSettings: '"opsz" 72' }}>{title}</h2>
          </div>
          {onClose && <button onClick={onClose} className="dx-focus" style={{ appearance: 'none', border: 'none', background: 'none', cursor: 'pointer', fontFamily: 'var(--font-ui)', fontSize: 12, fontWeight: 600, color: 'var(--ink-muted)', padding: 4 }}>Close ✕</button>}
        </div>
        <div style={{ padding: '12px 18px 14px', fontSize: 14, lineHeight: 1.45 }}>{children}</div>
        {actions && <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, padding: '10px 18px', borderTop: '1px solid var(--rule)' }}>{actions}</div>}
      </div>
    </div>
  );
}
