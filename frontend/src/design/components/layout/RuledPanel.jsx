import React from 'react';
import { SectionLabel } from './SectionLabel.jsx';

function Bars() {
  return (
    <div aria-hidden="true" style={{ display: 'grid', gap: 8, padding: '6px 0', color: 'var(--rule)' }}>
      {[92, 64, 80, 48].map((w, i) => <div key={i} className="dx-hatch" style={{ width: w + '%', height: 9 }} />)}
    </div>
  );
}

export function RuledPanel({ label, meta, count, rule = 'bold', columnRule = 'none', state = 'ready', emptyText = 'Nothing has printed in this column yet.', children, footer, style }) {
  const side = columnRule === 'left' ? { borderLeft: '1px solid var(--rule)', paddingLeft: 'calc(var(--gutter) / 2)' }
    : columnRule === 'right' ? { borderRight: '1px solid var(--rule)', paddingRight: 'calc(var(--gutter) / 2)' } : {};
  const metaNode = state === 'stale'
    ? <span style={{ color: 'var(--pencil-text)' }}>✎ Stale · last print {meta}</span>
    : state === 'error' ? <span style={{ color: 'var(--stamp)' }}>■ Wire down</span> : meta;
  return (
    <section aria-busy={state === 'loading'} style={{ minWidth: 0, ...side, ...style }}>
      {label && <SectionLabel rule={rule} count={count} meta={metaNode}>{label}</SectionLabel>}
      {state === 'loading' && (<div><Bars /><div style={{ fontSize: 'var(--text-small)', color: 'var(--ink-muted)', fontStyle: 'italic', fontFamily: 'var(--font-display)' }}>Setting type…</div></div>)}
      {state === 'empty' && <p style={{ margin: '8px 0', fontFamily: 'var(--font-display)', fontStyle: 'italic', color: 'var(--ink-muted)' }}>{emptyText}</p>}
      {state === 'error' && <p style={{ margin: '8px 0', fontFamily: 'var(--font-display)', fontStyle: 'italic', color: 'var(--ink-muted)' }}>The wire from the exchange dropped. Reconnecting, figures below are frozen.</p>}
      {(state === 'ready' || state === 'stale' || state === 'error') && children}
      {footer && <div style={{ borderTop: '1px solid var(--rule)', marginTop: 8, paddingTop: 6, fontSize: 'var(--text-small)', color: 'var(--ink-muted)' }}>{footer}</div>}
    </section>
  );
}
