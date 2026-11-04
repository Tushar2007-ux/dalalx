import React from 'react';

/* X-Ray switch styled as a newspaper cross-reference: "See the explainer →". On, it reads "Close the X-Ray ←". */
export function XRayToggle({ on = false, onChange = () => {}, label = 'X-Ray', offText = 'See the explainer →', onText = 'Close the explainer ←', style }) {
  const [h, setH] = React.useState(false);
  return (
    <button role="switch" aria-checked={on} onClick={() => onChange(!on)} onMouseEnter={() => setH(true)} onMouseLeave={() => setH(false)} className="dx-focus"
      style={{ appearance: 'none', border: 'none', background: 'none', padding: 0, cursor: 'pointer', display: 'inline-flex', alignItems: 'baseline', gap: 8, color: 'var(--ink)', textAlign: 'left', ...style }}>
      <span style={{ fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase', padding: '2px 6px', border: '1px solid var(--rule-strong)', background: on ? 'var(--ink)' : 'transparent', color: on ? 'var(--ink-inverse)' : 'var(--ink)' }}>
        {label} {on ? 'on' : 'off'}
      </span>
      <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 14, textDecoration: h ? 'underline' : 'none', textUnderlineOffset: 3 }}>{on ? onText : offText}</span>
    </button>
  );
}
