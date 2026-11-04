import React from 'react';

const TONE = {
  neutral: null,
  up: { glyph: '▲', color: 'var(--buy)', sr: 'Up: ' },
  down: { glyph: '▼', color: 'var(--sell)', sr: 'Down: ' },
};

export function HeadlineBanner({ kicker = 'Latest', headline, deck, time, tone = 'neutral', state = 'live', style }) {
  const t = TONE[tone];
  const breaking = state === 'breaking';
  const stale = state === 'stale';
  return (
    <div role="status" aria-live="polite" style={{ display: 'grid', gridTemplateColumns: 'auto minmax(0,1fr) auto', alignItems: 'baseline', gap: 16, padding: '10px 0', borderBottom: '1px solid var(--rule-strong)', ...style }}>
      <span style={{
        fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase',
        padding: '3px 7px', border: '1px solid var(--stamp)', whiteSpace: 'nowrap',
        background: breaking ? 'var(--stamp)' : 'transparent', color: breaking ? 'var(--ink-inverse)' : 'var(--stamp)',
      }}>{kicker}</span>
      <div style={{ minWidth: 0 }}>
        <div key={headline} className={state === 'live' || breaking ? 'dx-anim-typeset' : undefined}
          style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 'var(--text-headline)', lineHeight: 'var(--leading-tight)', fontVariationSettings: '"opsz" 72', color: stale ? 'var(--ink-muted)' : 'var(--ink)', textWrap: 'pretty' }}>
          {t && <span aria-hidden="true" style={{ color: t.color, fontSize: '0.7em', marginRight: 8, verticalAlign: '0.1em' }}>{t.glyph}</span>}
          {t && <span style={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }}>{t.sr}</span>}
          {headline}
        </div>
        {deck && <div style={{ marginTop: 4, fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 'var(--text-deck)', color: 'var(--ink-muted)' }}>{deck}</div>}
      </div>
      <span className="dx-num" style={{ fontSize: 12, color: stale ? 'var(--pencil-text)' : 'var(--ink-muted)', whiteSpace: 'nowrap' }}>
        {stale ? 'Stale · ' : ''}{time}
      </span>
    </div>
  );
}
