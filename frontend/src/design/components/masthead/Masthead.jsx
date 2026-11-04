import React from 'react';

const STATUS = {
  live: { glyph: '●', label: 'Market open', color: 'var(--buy)' },
  'pre-open': { glyph: '○', label: 'Pre-open session', color: 'var(--ink-muted)' },
  halted: { glyph: '■', label: 'Trading halted', color: 'var(--stamp)' },
  auction: { glyph: '◐', label: 'Call auction', color: 'var(--pencil-text)' },
  closed: { glyph: '■', label: 'Market closed', color: 'var(--ink-muted)' },
};

const meta = { fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 600, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase', color: 'var(--ink-muted)' };

export function Masthead({
  title = 'DalalX',
  place = 'Prayagraj',
  date = 'Wednesday, 28 October 2026',
  session = 14,
  status = 'live',
  editionName,
  leftMeta = 'Vol. I · A simulated exchange',
  rightMeta = 'Paper money only',
  compact = false,
  strip = false,
  right,
  style,
}) {
  const s = STATUS[status] || STATUS.live;
  const ed = editionName || 'Day Edition';
  if (strip) {
    return (
      <header style={{ color: 'var(--ink)', display: 'flex', alignItems: 'center', gap: 18, flexWrap: 'wrap', padding: '7px 0 6px', borderBottom: '4px double var(--rule-strong)', ...style }}>
        <h1 style={{ margin: 0, fontFamily: 'var(--font-display)', fontWeight: 800, fontSize: 26, lineHeight: 1, letterSpacing: 'var(--tracking-masthead)', fontVariationSettings: '"opsz" 144' }}>{title}</h1>
        <span aria-hidden="true" style={{ alignSelf: 'stretch', borderLeft: '1px solid var(--rule)' }} />
        <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 14, whiteSpace: 'nowrap' }}>
          {place} · {date} · Session No. <span className="dx-num" style={{ fontStyle: 'normal', fontSize: 13 }}>{session}</span>
        </span>
        <span style={{ flex: 1 }} />
        <span role="status" style={{ ...meta, color: s.color, display: 'flex', gap: 6, alignItems: 'center' }}><span aria-hidden="true">{s.glyph}</span>{s.label}</span>
        <span aria-hidden="true" style={{ alignSelf: 'stretch', borderLeft: '1px solid var(--rule)' }} />
        <span style={meta}>{ed}</span>
        {right}
      </header>
    );
  }
  return (
    <header style={{ color: 'var(--ink)', ...style }}>
      {!compact && (
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, padding: '6px 0', borderBottom: '1px solid var(--rule)', ...meta }}>
          <span>{leftMeta}</span>
          <span>{rightMeta}</span>
        </div>
      )}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr auto 1fr', alignItems: 'end', gap: 16, padding: compact ? '6px 0 4px' : '10px 0 6px' }}>
        <span style={{ ...meta, alignSelf: 'center' }}>{compact ? ed : ''}</span>
        <h1 style={{ margin: 0, fontFamily: 'var(--font-display)', fontWeight: 800, fontSize: compact ? 36 : 'var(--text-masthead)', lineHeight: 1, letterSpacing: 'var(--tracking-masthead)', fontVariationSettings: '"opsz" 144, "SOFT" 0, "WONK" 0', textAlign: 'center' }}>{title}</h1>
        <span style={{ justifySelf: 'end', alignSelf: 'center' }}>{compact ? right : null}</span>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr auto 1fr', alignItems: 'center', gap: 16, padding: '5px 0', borderTop: '1px solid var(--rule-strong)', borderBottom: '4px double var(--rule-strong)' }}>
        <span style={meta}>{compact ? '' : ed}</span>
        <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 14, whiteSpace: 'nowrap' }}>
          {place} · {date} · Session No. <span className="dx-num" style={{ fontStyle: 'normal', fontSize: 13 }}>{session}</span>
        </span>
        <span role="status" style={{ ...meta, color: s.color, justifySelf: 'end', display: 'flex', gap: 6, alignItems: 'center' }}>
          <span aria-hidden="true">{s.glyph}</span>{s.label}
        </span>
      </div>
    </header>
  );
}
