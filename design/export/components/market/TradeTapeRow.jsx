import React from 'react';
import { BOTS } from '../bots/Portrait.jsx';
import { Monogram } from '../bots/Monogram.jsx';

const fmt = (n, d = 2) => Number(n).toLocaleString('en-IN', { minimumFractionDigits: d, maximumFractionDigits: d });
const nameOf = (k, fallback) => (k === 'you' ? 'You' : (BOTS[k] && BOTS[k].name) || fallback || k);

function Head({ k }) {
  return <Monogram bot={k} size={20} />;
}

/* Parties render as two ink-stamp monograms, buyer ← seller; full names on hover/focus. Falls back to text when bot keys are absent. */
export function TradeTapeRow({ time, symbol, side = 'buy', price, qty, buyer, seller, buyerBot, sellerBot, fresh = false, mine = false, flagged = false, style }) {
  const [tip, setTip] = React.useState(false);
  const c = side === 'buy' ? 'var(--buy)' : 'var(--sell)';
  const heads = buyerBot && sellerBot;
  const label = 'Bought by ' + nameOf(buyerBot, buyer) + ', sold by ' + nameOf(sellerBot, seller);
  return (
    <div role="row" className={fresh ? 'dx-anim-typeset' : undefined}
      style={{
        position: 'relative',
        display: 'grid', gridTemplateColumns: heads ? '84px 30px 92px minmax(0,1fr) 56px 58px' : '88px 34px 58px minmax(0,1fr) 58px minmax(0,1.3fr)', alignItems: 'center', columnGap: 10,
        height: 'var(--row-tape)', borderBottom: '1px dotted var(--rule)', fontSize: 12.5, background: mine ? 'var(--surface)' : 'transparent', ...style,
      }}>
      <span className="dx-num" style={{ color: 'var(--ink-muted)', fontSize: 11.5 }}>{time}</span>
      <span aria-label={side === 'buy' ? 'Buy' : 'Sell'} style={{ color: c, fontFamily: 'var(--font-ui)', fontWeight: 700, fontSize: 11, letterSpacing: '0.06em' }}>
        <span aria-hidden="true">{side === 'buy' ? '▲ B' : '▼ S'}</span>
      </span>
      <span style={{ fontFamily: 'var(--font-ui)', fontWeight: 600, fontSize: 12, letterSpacing: '0.04em' }}>{symbol}</span>
      <span className="dx-num" style={{ textAlign: 'right', fontWeight: 500 }}>{fmt(price)}</span>
      <span className="dx-num" style={{ textAlign: 'right', color: 'var(--ink-muted)' }}>×{Number(qty).toLocaleString('en-IN')}</span>
      {heads ? (
        <span tabIndex={0} aria-label={label} className="dx-focus" onMouseEnter={() => setTip(true)} onMouseLeave={() => setTip(false)} onFocus={() => setTip(true)} onBlur={() => setTip(false)}
          style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'flex-end', gap: 3, cursor: 'help', position: 'relative' }}>
          {flagged && <span aria-hidden="true" style={{ color: 'var(--pencil-text)', fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 12 }}>✎</span>}
          <Head k={buyerBot} />
          <span aria-hidden="true" style={{ fontSize: 10, color: 'var(--ink-muted)' }}>←</span>
          <Head k={sellerBot} />
          {tip && (
            <span role="tooltip" style={{ position: 'absolute', right: 0, bottom: 'calc(100% + 4px)', zIndex: 5, whiteSpace: 'nowrap', background: 'var(--surface)', border: '1px solid var(--rule-strong)', padding: '4px 8px', fontSize: 12, color: 'var(--ink)', display: 'grid', gap: 1 }}>
              <span><span style={{ color: 'var(--buy)', fontWeight: 600 }}>▲ Buyer</span> {nameOf(buyerBot)}</span>
              <span><span style={{ color: 'var(--sell)', fontWeight: 600 }}>▼ Seller</span> {nameOf(sellerBot)}</span>
              {flagged && <span style={{ color: 'var(--pencil-text)', fontFamily: 'var(--font-display)', fontStyle: 'italic' }}>✎ Flagged by surveillance</span>}
            </span>
          )}
        </span>
      ) : (
        <span style={{ fontSize: 12, color: 'var(--ink-muted)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {flagged && <span style={{ color: 'var(--pencil-text)', fontFamily: 'var(--font-display)', fontStyle: 'italic', marginRight: 4 }}>✎</span>}
          {mine ? <strong style={{ color: 'var(--ink)', fontWeight: 600 }}>{side === 'buy' ? 'You' : seller}</strong> : null}
          {mine ? (side === 'buy' ? ' ← ' + seller : ' → You') : (buyer + ' ← ' + seller)}
        </span>
      )}
    </div>
  );
}
