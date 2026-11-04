import React from 'react';

export function MarginNote({ children, author, side = 'right', tone = 'pencil', time, style }) {
  const c = tone === 'pencil' ? 'var(--pencil-text)' : 'var(--ink)';
  const arrow = side === 'left' ? '→' : side === 'below' ? '↑' : '←';
  return (
    <aside role="note" style={{ display: 'flex', gap: 6, alignItems: 'baseline', maxWidth: 220, color: c, ...style }}>
      {side !== 'left' && <span aria-hidden="true" style={{ fontSize: 15, lineHeight: 1 }}>{arrow}</span>}
      <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 14, lineHeight: 1.3, textWrap: 'pretty' }}>
        {children}
        {(author || time) && <span style={{ display: 'block', marginTop: 2, fontStyle: 'normal', fontFamily: 'var(--font-mono)', fontSize: 10.5, letterSpacing: '0.04em' }}>{author ? '— ' + author : ''}{author && time ? ' · ' : ''}{time}</span>}
      </span>
      {side === 'left' && <span aria-hidden="true" style={{ fontSize: 15, lineHeight: 1 }}>{arrow}</span>}
    </aside>
  );
}
