import React from 'react';

const SIZES = { sm: { h: 28, px: 10, fs: 12 }, md: { h: 36, px: 16, fs: 13 }, lg: { h: 44, px: 22, fs: 14 } };

export function Button({ variant = 'primary', size = 'md', disabled = false, loading = false, state, fullWidth = false, kbd, children, onClick, type = 'button', style, ...rest }) {
  const [h, setH] = React.useState(false);
  const [p, setP] = React.useState(false);
  const hover = state === 'hover' || (h && !disabled);
  const press = state === 'active' || (p && !disabled);
  const focus = state === 'focus';
  const s = SIZES[size];
  const solid = variant === 'primary' || variant === 'danger';
  const fill = variant === 'danger' ? 'var(--sell)' : 'var(--ink)';
  const onFill = variant === 'danger' ? 'var(--on-sell)' : 'var(--ink-inverse)';
  let look;
  if (disabled) look = { background: 'transparent', color: 'var(--ink-muted)', border: '1px dashed var(--ink-muted)' };
  else if (solid) look = { background: fill, color: onFill, border: '1px solid ' + fill, boxShadow: hover ? 'inset 0 0 0 2px var(--paper)' : 'none' };
  else if (variant === 'secondary') look = { background: hover ? 'var(--surface-sunk)' : 'transparent', color: 'var(--ink)', border: '1px solid var(--rule-strong)' };
  else look = { background: 'transparent', color: 'var(--ink)', border: '1px solid transparent', textDecoration: hover ? 'underline' : 'none', textUnderlineOffset: 3 };
  return (
    <button
      type={type} disabled={disabled} aria-busy={loading || undefined} onClick={loading ? undefined : onClick}
      onMouseEnter={() => setH(true)} onMouseLeave={() => { setH(false); setP(false); }}
      onMouseDown={() => setP(true)} onMouseUp={() => setP(false)} className="dx-focus"
      style={{
        appearance: 'none', borderRadius: 0, cursor: disabled ? 'not-allowed' : loading ? 'progress' : 'pointer',
        height: s.h, padding: '0 ' + s.px + 'px', width: fullWidth ? '100%' : undefined,
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
        fontFamily: 'var(--font-ui)', fontWeight: 600, fontSize: s.fs, letterSpacing: '0.02em', whiteSpace: 'nowrap',
        transform: press ? 'translateY(1px)' : 'none', outline: focus ? '2px solid var(--focus)' : undefined, outlineOffset: 2,
        transition: 'background-color var(--dur-tick) var(--ease-out), box-shadow var(--dur-tick) var(--ease-out)',
        ...look, ...style,
      }}
      {...rest}
    >
      {loading ? <span>Setting<span className="dx-num">…</span></span> : children}
      {kbd && !loading && <span className="dx-num" style={{ fontSize: s.fs - 2, opacity: 0.85, border: '1px solid currentColor', padding: '0 4px', lineHeight: '15px' }}>{kbd}</span>}
    </button>
  );
}
