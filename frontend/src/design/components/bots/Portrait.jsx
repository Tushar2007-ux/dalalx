import React from 'react';

export const BOTS = {
  munim: { key: 'munim', name: 'The Munim', mono: 'MU', role: 'Market Maker', line: 'Quotes both sides all session and keeps the spread honest.' },
  momentum: { key: 'momentum', name: 'Momentum Chaser', mono: 'MC', role: 'Trend Follower', line: 'Buys what is rising. Sells what is falling.' },
  panic: { key: 'panic', name: 'Panic Seller', mono: 'PS', role: 'Liquidity Taker', line: 'Sells first, reads the news later.' },
  whale: { key: 'whale', name: 'The Whale', mono: 'WH', role: 'Block Trader', line: 'Moves size slowly. The book notices anyway.' },
  spoofer: { key: 'spoofer', name: 'The Spoofer', mono: 'SP', role: 'Layering Bot', line: 'Shows orders it never means to fill.' },
  inspector: { key: 'inspector', name: 'Surveillance Inspector', mono: 'SI', role: 'Market Surveillance', line: 'Reads every line of the tape. Twice.' },
};

function base() {
  return (typeof window !== 'undefined' && window.DALALX_ASSET_BASE) || '/assets/portraits/';
}

/* Ink-only PNGs (transparent paper). <48px → tight head crop. Night shows the light-ink print; `plate` shows the day print on a light inset instead. */
export function Portrait({ bot = 'munim', size = 160, crop = 'auto', plate = false, frame, alt, file, style }) {
  const b = BOTS[bot] || BOTS.munim;
  const head = crop === 'head' || (crop === 'auto' && size < 48);
  const stem = base() + (file || b.key) + (head ? '-head' : '');
  const showFrame = frame ?? head;
  const img = { width: '100%', height: '100%', objectFit: 'cover' };
  return (
    <span style={{
      display: 'inline-block', flex: 'none', width: size, height: size, overflow: 'hidden', verticalAlign: 'middle',
      background: plate ? '#F8EADF' : 'transparent', border: showFrame ? '1px solid var(--rule)' : 'none', boxSizing: 'border-box', ...style,
    }}>
      {plate
        ? <img src={stem + '-day.png'} alt={alt ?? b.name} style={{ ...img, display: 'block', mixBlendMode: 'multiply' }} />
        : <>
            <img className="dx-portrait-day" src={stem + '-day.png'} alt={alt ?? b.name} style={img} />
            <img className="dx-portrait-night" src={stem + '-night.png'} alt={alt ?? b.name} style={img} />
          </>}
    </span>
  );
}
