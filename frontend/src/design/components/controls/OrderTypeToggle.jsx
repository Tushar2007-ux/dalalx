import React from 'react';

const DEFAULTS = [
  { value: 'limit', label: 'Limit', hint: 'Rests in the book at your price.' },
  { value: 'market', label: 'Market', hint: 'Fills now at the best available price.' },
  { value: 'ioc', label: 'IOC', hint: 'Immediate or cancel: fills what it can, cancels the rest.' },
];

export function OrderTypeToggle({ value = 'limit', onChange = () => {}, options = DEFAULTS, disabled = false, showHint = true, hoverValue, focusValue, style }) {
  const [h, setH] = React.useState(null);
  const current = options.find((o) => o.value === value);
  const onKey = (e) => {
    const i = options.findIndex((o) => o.value === value);
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') { e.preventDefault(); onChange(options[(i + 1) % options.length].value); }
    if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') { e.preventDefault(); onChange(options[(i - 1 + options.length) % options.length].value); }
  };
  return (
    <div style={style}>
      <div role="radiogroup" aria-label="Order type" aria-disabled={disabled || undefined} onKeyDown={onKey}
        style={{ display: 'grid', gridTemplateColumns: 'repeat(' + options.length + ', 1fr)', border: '1px ' + (disabled ? 'dashed var(--ink-muted)' : 'solid var(--rule-strong)') }}>
        {options.map((o, i) => {
          const on = o.value === value;
          const hover = !disabled && !on && (hoverValue === o.value || h === o.value);
          const focus = focusValue === o.value;
          return (
            <button key={o.value} role="radio" aria-checked={on} tabIndex={on ? 0 : -1} disabled={disabled}
              onClick={() => onChange(o.value)} onMouseEnter={() => setH(o.value)} onMouseLeave={() => setH(null)} className="dx-focus"
              style={{
                appearance: 'none', border: 'none', borderLeft: i ? '1px solid ' + (disabled ? 'var(--rule)' : 'var(--rule-strong)') : 'none', borderRadius: 0,
                height: 32, cursor: disabled ? 'not-allowed' : 'pointer', fontFamily: 'var(--font-ui)', fontWeight: 600, fontSize: 13,
                background: on && !disabled ? 'var(--ink)' : hover ? 'var(--surface-sunk)' : 'transparent',
                color: disabled ? 'var(--ink-muted)' : on ? 'var(--ink-inverse)' : 'var(--ink)',
                outline: focus ? '2px solid var(--focus)' : undefined, outlineOffset: 2, position: 'relative', zIndex: focus ? 1 : 0,
                textDecoration: disabled && on ? 'underline' : 'none',
                transition: 'background-color var(--dur-tick) var(--ease-out)',
              }}>{o.label}</button>
          );
        })}
      </div>
      {showHint && current && <div style={{ marginTop: 5, fontSize: 'var(--text-small)', color: 'var(--ink-muted)', fontFamily: 'var(--font-display)', fontStyle: 'italic' }}>{current.hint}</div>}
    </div>
  );
}
