import React from 'react';
import { BOTS } from './Portrait.jsx';

/* Ink-stamp monogram for small sizes (portraits are reserved for 40px and up). 'you' renders YOU in stamp red. */
export function Monogram({ bot = 'munim', size = 20, angle = -6, title, style }) {
  const you = bot === 'you';
  const b = BOTS[bot];
  const text = you ? 'YOU' : (b && b.mono) || String(bot).slice(0, 2).toUpperCase();
  const c = you ? 'var(--stamp)' : 'var(--ink)';
  return (
    <span role="img" aria-label={title || (you ? 'You' : (b && b.name) || bot)}
      style={{
        width: size, height: size, flex: 'none', boxSizing: 'border-box', borderRadius: '50%', display: 'inline-grid', placeItems: 'center',
        border: '1.5px solid ' + c, outline: '0.75px solid ' + c, outlineOffset: 1, color: c, transform: 'rotate(' + angle + 'deg)',
        fontFamily: 'var(--font-ui)', fontWeight: 700, fontSize: Math.round(size * (text.length > 2 ? 0.32 : 0.4)), letterSpacing: '0.02em', lineHeight: 1,
        background: 'var(--paper)', mixBlendMode: 'var(--portrait-blend)', ...style,
      }}>{text}</span>
  );
}
