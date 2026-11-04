import React from 'react';

/* News-in-brief column: the last few headlines, newest first, each with a timestamp. Newest typesets in. */
export function TheWire({ items = [], limit = 5, style }) {
  return (
    <ol aria-label="The Wire" style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', ...style }}>
      {items.slice(0, limit).map((it, i) => {
        const g = it.tone === 'up' ? '▲' : it.tone === 'down' ? '▼' : it.tone === 'alert' ? '■' : null;
        const c = it.tone === 'up' ? 'var(--buy)' : it.tone === 'down' ? 'var(--sell)' : 'var(--stamp)';
        return (
          <li key={it.id ?? it.time + it.headline} className={i === 0 && it.fresh ? 'dx-anim-typeset' : undefined}
            style={{ display: 'grid', gridTemplateColumns: '44px minmax(0,1fr)', gap: 8, padding: '6px 0', borderBottom: '1px solid var(--rule)' }}>
            <span className="dx-num" style={{ fontSize: 11, color: 'var(--ink-muted)', paddingTop: 2 }}>{it.time}</span>
            <span style={{ fontFamily: 'var(--font-display)', fontSize: 14, lineHeight: 1.25, fontWeight: i === 0 ? 600 : 400, textWrap: 'pretty' }}>
              {g && <span aria-hidden="true" style={{ color: c, fontSize: 10, marginRight: 5, verticalAlign: '0.1em' }}>{g}</span>}
              {it.headline}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
