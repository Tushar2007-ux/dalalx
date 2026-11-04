import React from 'react';

/* Advancing vs declining: hatched buy ink from the left, hatched sell ink from the right, unchanged/halted in rule between. */
export function BreadthBar({ advancing = 0, declining = 0, unchanged = 0, halted = 0, style }) {
  const total = Math.max(1, advancing + declining + unchanged + halted);
  const seg = (n, c, edge) => n > 0 && (
    <span style={{ position: 'relative', width: (n / total) * 100 + '%', color: c, [edge]: '1.5px solid ' + c }} className="dx-depth">
      <span className="dx-hatch" style={{ position: 'absolute', inset: 0, opacity: 0.55 }} />
    </span>
  );
  return (
    <div style={{ display: 'grid', gap: 6, ...style }}>
      <div role="img" aria-label={advancing + ' advancing, ' + declining + ' declining, ' + unchanged + ' unchanged, ' + halted + ' halted'}
        style={{ display: 'flex', height: 14, border: '1px solid var(--rule-strong)' }}>
        {seg(advancing, 'var(--buy)', 'borderRight')}
        {unchanged + halted > 0 && <span style={{ width: ((unchanged + halted) / total) * 100 + '%', background: 'var(--surface-sunk)' }} />}
        {seg(declining, 'var(--sell)', 'borderLeft')}
      </div>
      <div className="dx-num" style={{ display: 'flex', justifyContent: 'space-between', gap: 8, fontSize: 11.5, flexWrap: 'wrap' }}>
        <span style={{ color: 'var(--buy)' }}>▲ {advancing} advancing</span>
        {(unchanged + halted) > 0 && <span style={{ color: 'var(--ink-muted)' }}>{halted ? '■ ' + halted + ' halted' : '· ' + unchanged + ' unch'}</span>}
        <span style={{ color: 'var(--sell)' }}>▼ {declining} declining</span>
      </div>
    </div>
  );
}
