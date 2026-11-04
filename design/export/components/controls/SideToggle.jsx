import React from 'react';

const SIDES = [
  { value: 'buy', label: 'Buy', glyph: '▲', c: 'var(--buy)', on: 'var(--on-buy)', wash: 'var(--buy-wash)' },
  { value: 'sell', label: 'Sell', glyph: '▼', c: 'var(--sell)', on: 'var(--on-sell)', wash: 'var(--sell-wash)' },
];

export function SideToggle({ value = 'buy', onChange = () => {}, disabled = false, hoverValue, focusValue, style }) {
  const [h, setH] = React.useState(null);
  const onKey = (e) => {
    if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(e.key)) { e.preventDefault(); onChange(value === 'buy' ? 'sell' : 'buy'); }
  };
  return (
    <div role="radiogroup" aria-label="Side" onKeyDown={onKey} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6, ...style }}>
      {SIDES.map((s) => {
        const on = s.value === value;
        const hover = !disabled && !on && (hoverValue === s.value || h === s.value);
        const focus = focusValue === s.value;
        return (
          <button key={s.value} role="radio" aria-checked={on} tabIndex={on ? 0 : -1} disabled={disabled}
            onClick={() => onChange(s.value)} onMouseEnter={() => setH(s.value)} onMouseLeave={() => setH(null)} className="dx-focus"
            style={{
              appearance: 'none', borderRadius: 0, height: 40, cursor: disabled ? 'not-allowed' : 'pointer',
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
              fontFamily: 'var(--font-ui)', fontWeight: 700, fontSize: 13, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase',
              border: '1px ' + (disabled ? 'dashed var(--ink-muted)' : 'solid ' + s.c),
              background: disabled ? 'transparent' : on ? s.c : hover ? s.wash : 'transparent',
              color: disabled ? 'var(--ink-muted)' : on ? s.on : s.c,
              outline: focus ? '2px solid var(--focus)' : undefined, outlineOffset: 2,
              transition: 'background-color var(--dur-tick) var(--ease-out)',
            }}>
            <span aria-hidden="true">{s.glyph}</span>{s.label}
          </button>
        );
      })}
    </div>
  );
}
