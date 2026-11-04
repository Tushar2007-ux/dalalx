import React from 'react';

const KINDS = {
  'stop-press': { text: 'Stop Press', color: 'var(--stamp)' },
  'under-investigation': { text: 'Under Investigation', color: 'var(--stamp)' },
  filled: { text: 'Filled', color: 'var(--buy)', glyph: '✓' },
};
const SIZES = { sm: { fs: 11, pad: '3px 7px', b: 2 }, md: { fs: 15, pad: '5px 12px', b: 3 }, lg: { fs: 24, pad: '8px 18px', b: 4 } };

/* Slams in once on mount (or when `slamKey` changes). Never re-slams on data ticks. */
export function RubberStamp({ kind = 'stop-press', text, sub, size = 'md', angle = -6, color, animate = false, slamKey, backed = false, style }) {
  const k = KINDS[kind] || KINDS['stop-press'];
  const s = SIZES[size];
  const c = color || k.color;
  return (
    <span role="img" aria-label={(text || k.text) + (sub ? ', ' + sub : '')} style={{ display: 'inline-block', pointerEvents: 'none', ...style }}>
      <span key={slamKey} className={animate ? 'dx-anim-stamp' : undefined}
        style={{
          '--stamp-angle': angle + 'deg', display: 'inline-grid', justifyItems: 'center', gap: 2, transform: 'rotate(' + angle + 'deg)',
          padding: s.pad, border: s.b + 'px solid ' + c, outline: '1px solid ' + c, outlineOffset: 2, borderRadius: 'var(--radius-stamp)',
          color: c, mixBlendMode: backed ? 'normal' : 'var(--portrait-blend)', opacity: backed ? 1 : 0.94, background: backed ? 'var(--paper)' : 'transparent', whiteSpace: 'nowrap',
        }}>
        <span style={{ fontFamily: 'var(--font-ui)', fontWeight: 700, fontSize: s.fs, lineHeight: 1, letterSpacing: '0.14em', textTransform: 'uppercase' }}>
          {k.glyph && <span aria-hidden="true" style={{ marginRight: '0.4em' }}>{k.glyph}</span>}{text || k.text}
        </span>
        {sub && <span className="dx-num" style={{ fontSize: Math.max(9, Math.round(s.fs * 0.55)), letterSpacing: '0.06em' }}>{sub}</span>}
      </span>
    </span>
  );
}
