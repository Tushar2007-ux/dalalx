import React from 'react';

const label = { fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase', color: 'var(--ink)' };

export function PriceInput({ label: text, value, onChange = () => {}, prefix, suffix, placeholder, hint, error, state, disabled = false, readOnly = false, id, inputMode = 'decimal', align = 'right', style }) {
  const [f, setF] = React.useState(false);
  const isDisabled = disabled || state === 'disabled';
  const isError = !!error || state === 'error';
  const focus = state === 'focus' || f;
  const inputId = id || 'dx-in-' + (text || 'field').toLowerCase().replace(/\W+/g, '-');
  const border = isDisabled ? '1px dashed var(--ink-muted)' : isError ? '1px solid var(--pencil)' : focus ? '1px solid var(--rule-strong)' : '1px solid var(--rule-strong)';
  const ring = isError ? 'inset 0 0 0 1px var(--pencil)' : focus && !isDisabled ? 'inset 0 0 0 1px var(--rule-strong)' : 'none';
  const affix = { display: 'flex', alignItems: 'center', padding: '0 10px', fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--ink-muted)', background: isDisabled ? 'transparent' : 'var(--surface-sunk)' };
  return (
    <div style={{ display: 'grid', gap: 5, ...style }}>
      {text && <label htmlFor={inputId} style={label}>{text}</label>}
      <div style={{ display: 'flex', height: 'var(--control-h)', border, boxShadow: ring, background: readOnly ? 'transparent' : 'var(--surface)' }}>
        {prefix && <span aria-hidden="true" style={{ ...affix, borderRight: '1px solid var(--rule)' }}>{prefix}</span>}
        <input id={inputId} value={value} placeholder={placeholder} disabled={isDisabled} readOnly={readOnly} inputMode={inputMode}
          aria-invalid={isError || undefined} aria-describedby={inputId + '-msg'}
          onChange={(e) => onChange(e.target.value)} onFocus={() => setF(true)} onBlur={() => setF(false)} className="dx-num"
          size={1} style={{ flex: 1, minWidth: 0, width: '100%', border: 'none', outline: 'none', background: 'transparent', padding: '0 10px', textAlign: align, fontSize: 15, fontWeight: 500, color: isDisabled ? 'var(--ink-muted)' : 'var(--ink)' }} />
        {suffix && <span style={{ ...affix, borderLeft: '1px solid var(--rule)', fontFamily: 'var(--font-ui)', fontSize: 12 }}>{suffix}</span>}
      </div>
      {(error || hint) && (
        <div id={inputId + '-msg'} style={{ fontSize: 'var(--text-small)', color: isError ? 'var(--pencil-text)' : 'var(--ink-muted)', fontFamily: isError ? 'var(--font-display)' : 'var(--font-ui)', fontStyle: isError ? 'italic' : 'normal' }}>
          {isError ? '✎ ' + error : hint}
        </div>
      )}
    </div>
  );
}
