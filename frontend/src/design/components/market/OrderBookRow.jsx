import React from 'react';
import { RedPencilCircle } from '../marks/RedPencilCircle.jsx';
import { Monogram } from '../bots/Monogram.jsx';

const fmt = (n, d = 2) => Number(n).toLocaleString('en-IN', { minimumFractionDigits: d, maximumFractionDigits: d });

/* depthStyle="track" (default): hatched bar in its own 96px column after the figures, bids grow from the left, asks from the right,
   so the book has a shape and no number is ever overlapped. depthStyle="row": bar behind the whole row (decorative views only). */
export function OrderBookRow({ side = 'bid', price, qty, orders, depth = 0, best = false, mine = false, flagged = false, bot, xray = false, depthStyle = 'track', hover, onClick, style }) {
  const [h, setH] = React.useState(false);
  const isHover = hover || h;
  const c = side === 'bid' ? 'var(--buy)' : 'var(--sell)';
  const glyph = side === 'bid' ? '▲' : '▼';
  const d = Math.max(0, Math.min(1, depth)) * 100 + '%';
  const fromLeft = side === 'bid';
  const row = depthStyle === 'row';
  const qtyText = Number(qty).toLocaleString('en-IN');
  return (
    <div role="row" aria-label={(side === 'bid' ? 'Bid ' : 'Ask ') + fmt(price) + ', ' + qty + ' shares' + (mine ? ', your order' : '') + (flagged ? ', flagged by surveillance' : '')}
      onClick={onClick ? () => onClick(price) : undefined} onMouseEnter={() => setH(true)} onMouseLeave={() => setH(false)}
      style={{
        position: 'relative', isolation: 'isolate',
        display: 'grid', /* Price and quantity keep a floor wide enough for the digits; the depth
           bar is the one column allowed to give its width back, so a narrow
           panel loses chart, not numbers. */
        gridTemplateColumns: row ? '14px minmax(58px,1fr) minmax(42px,0.8fr) 36px' : '14px minmax(58px,1fr) minmax(42px,0.8fr) 32px minmax(0,96px)', alignItems: 'center', columnGap: 8,
        height: 'var(--row-book)', padding: '0 6px 0 4px', borderBottom: '1px solid var(--rule)', cursor: onClick ? 'pointer' : 'default',
        background: isHover ? 'var(--surface-sunk)' : mine ? 'var(--surface)' : 'transparent', ...style,
      }}>
      {row && (
        <span aria-hidden="true" className="dx-depth" style={{ position: 'absolute', top: 3, bottom: 3, [fromLeft ? 'left' : 'right']: 0, width: d, zIndex: -1, color: c, ['border' + (fromLeft ? 'Right' : 'Left')]: depth > 0 ? '1.5px solid ' + c : 'none' }}>
          <span className="dx-hatch" style={{ position: 'absolute', inset: 0, opacity: 0.32 }} />
        </span>
      )}
      <span aria-hidden="true" style={{ fontSize: 9, color: best ? c : 'transparent' }}>{glyph}</span>
      <span className="dx-num" style={{ textAlign: 'right', fontSize: 'var(--text-num)', fontWeight: best ? 600 : 500, color: c }}>{fmt(price)}</span>
      <span className="dx-num" style={{ textAlign: 'right', fontSize: 'var(--text-num)', color: 'var(--ink)' }}>
        {flagged ? <RedPencilCircle draw={false} label="Flagged" style={{ padding: '0 6px' }}>{qtyText}</RedPencilCircle> : qtyText}
      </span>
      <span className="dx-num" style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', fontSize: 11.5, color: 'var(--ink-muted)' }}>
        {mine ? <span style={{ fontFamily: 'var(--font-ui)', fontWeight: 700, fontSize: 10, letterSpacing: '0.1em', color: 'var(--ink)' }}>YOU</span>
          : xray && bot ? <Monogram bot={bot} size={20} /> : orders}
      </span>
      {!row && (
        <span aria-hidden="true" style={{ height: 14, position: 'relative', ['border' + (fromLeft ? 'Left' : 'Right')]: '1px solid var(--rule)' }}>
          <span className="dx-hatch dx-depth" style={{ position: 'absolute', [fromLeft ? 'left' : 'right']: 0, top: 0, bottom: 0, width: d, color: c, ['border' + (fromLeft ? 'Right' : 'Left')]: depth > 0 ? '1.5px solid ' + c : 'none' }} />
        </span>
      )}
    </div>
  );
}
