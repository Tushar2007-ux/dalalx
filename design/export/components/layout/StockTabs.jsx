import React from 'react';

function Tab({ tab, active, hover, focus, onSelect, onKey }) {
  const [h, setH] = React.useState(false);
  const isHover = hover || h;
  const up = tab.change > 0, down = tab.change < 0;
  const changeColor = active ? 'var(--ink-inverse)' : up ? 'var(--buy)' : down ? 'var(--sell)' : 'var(--ink-muted)';
  return (
    <button
      role="tab" aria-selected={active} aria-disabled={tab.halted || undefined} tabIndex={active ? 0 : -1}
      onClick={() => !tab.halted && onSelect(tab.symbol)} onKeyDown={onKey}
      onMouseEnter={() => setH(true)} onMouseLeave={() => setH(false)} className="dx-focus"
      style={{
        appearance: 'none', cursor: tab.halted ? 'not-allowed' : 'pointer', borderRadius: 0,
        display: 'grid', gap: 1, textAlign: 'left', padding: '6px 12px 5px', minWidth: 92,
        border: '1px solid var(--rule-strong)', borderBottom: 'none', marginRight: -1,
        background: active ? 'var(--ink)' : isHover && !tab.halted ? 'var(--surface-sunk)' : 'var(--surface)',
        color: active ? 'var(--ink-inverse)' : tab.halted ? 'var(--ink-muted)' : 'var(--ink)',
        outline: focus ? '2px solid var(--focus)' : undefined, outlineOffset: 2, position: 'relative', zIndex: focus ? 1 : 0,
        transition: 'background-color var(--dur-tick) var(--ease-out)',
      }}>
      <span style={{ fontFamily: 'var(--font-ui)', fontWeight: 700, fontSize: 12, letterSpacing: '0.08em', textTransform: 'uppercase', textDecoration: tab.halted ? 'line-through' : 'none' }}>{tab.symbol}</span>
      <span className="dx-num" style={{ fontSize: 11.5, color: tab.halted ? 'var(--ink-muted)' : changeColor }}>
        {tab.halted ? '■ Halted' : (up ? '▲ ' : down ? '▼ ' : '· ') + (tab.change > 0 ? '+' : '') + tab.change.toFixed(2) + '%'}
      </span>
    </button>
  );
}

export function StockTabs({ tabs = [], value, onChange = () => {}, hoverSymbol, focusSymbol, style }) {
  const onKey = (e) => {
    const live = tabs.filter((t) => !t.halted);
    const i = live.findIndex((t) => t.symbol === value);
    if (e.key === 'ArrowRight') onChange(live[(i + 1) % live.length].symbol);
    if (e.key === 'ArrowLeft') onChange(live[(i - 1 + live.length) % live.length].symbol);
  };
  return (
    <div role="tablist" aria-label="Stocks" style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-end', borderBottom: '2px solid var(--rule-strong)', ...style }}>
      {tabs.map((t) => (
        <Tab key={t.symbol} tab={t} active={t.symbol === value} hover={hoverSymbol === t.symbol} focus={focusSymbol === t.symbol} onSelect={onChange} onKey={onKey} />
      ))}
    </div>
  );
}
