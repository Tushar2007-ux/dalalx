import React from 'react';

/* Page foot: "Page 1 · Markets · DalalX" between rules. */
export function Folio({ page = 1, section = 'Markets', title = 'DalalX', right, style }) {
  return (
    <footer style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 12, borderTop: '3px double var(--rule-strong)', padding: '6px 0 0', ...style }}>
      <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 13 }}>Page <span className="dx-num" style={{ fontStyle: 'normal', fontSize: 12 }}>{page}</span> · {section} · {title}</span>
      {right && <span style={{ fontSize: 'var(--text-small)', color: 'var(--ink-muted)' }}>{right}</span>}
    </footer>
  );
}
