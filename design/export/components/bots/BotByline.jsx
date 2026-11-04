import React from 'react';
import { Portrait, BOTS } from './Portrait.jsx';
import { RubberStamp } from '../marks/RubberStamp.jsx';

const STATUS = {
  active: { glyph: '●', label: 'Trading', color: 'var(--buy)' },
  idle: { glyph: '○', label: 'Idle', color: 'var(--ink-muted)' },
  flagged: { glyph: '◆', label: 'Flagged by surveillance', color: 'var(--pencil-text)' },
  halted: { glyph: '■', label: 'Suspended', color: 'var(--stamp)' },
  duty: { glyph: '◉', label: 'On duty', color: 'var(--ink)' },
};
const small = { fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase' };

function Stat({ s }) {
  const c = s.dir === 'up' ? 'var(--buy)' : s.dir === 'down' ? 'var(--sell)' : 'var(--ink)';
  return (
    <div style={{ display: 'grid', gap: 1, borderTop: '1px solid var(--rule)', paddingTop: 4 }}>
      <dt style={{ ...small, fontWeight: 600, fontSize: 10, color: 'var(--ink-muted)' }}>{s.label}</dt>
      <dd className="dx-num" style={{ margin: 0, fontSize: 13, fontWeight: 500, color: c }}>{s.dir === 'up' ? '▲ ' : s.dir === 'down' ? '▼ ' : ''}{s.value}</dd>
    </div>
  );
}

export function BotByline({ bot = 'munim', stats = [], status = 'active', layout = 'card', portraitSize = 168, selected = false, hover, onClick, style }) {
  const [h, setH] = React.useState(false);
  const b = BOTS[bot] || BOTS.munim;
  const st = STATUS[status] || STATUS.active;
  const isHover = onClick && (hover || h);
  const interactive = onClick ? { role: 'button', tabIndex: 0, onClick, onKeyDown: (e) => (e.key === 'Enter' || e.key === ' ') && onClick(), onMouseEnter: () => setH(true), onMouseLeave: () => setH(false), className: 'dx-focus' } : {};
  const muted = status === 'halted';

  if (layout === 'compact') {
    const s0 = stats[0];
    return (
      <div {...interactive} style={{ display: 'grid', gridTemplateColumns: '40px minmax(0,1fr) auto', alignItems: 'center', gap: 10, padding: '6px 4px', borderBottom: '1px solid var(--rule)', background: selected ? 'var(--surface)' : isHover ? 'var(--surface-sunk)' : 'transparent', cursor: onClick ? 'pointer' : 'default', ...style }}>
        <Portrait bot={bot} size={40} />
        <div style={{ minWidth: 0 }}>
          <div style={{ fontFamily: 'var(--font-display)', fontWeight: 600, fontSize: 15, lineHeight: 1.15, color: muted ? 'var(--ink-muted)' : 'var(--ink)' }}>{b.name}</div>
          <div style={{ fontSize: 11.5, color: st.color }}><span aria-hidden="true">{st.glyph}</span> {b.role} · {st.label}</div>
        </div>
        {s0 && <div style={{ textAlign: 'right' }}><Stat s={s0} /></div>}
      </div>
    );
  }

  return (
    <article {...interactive} style={{ display: 'grid', gap: 8, alignContent: 'start', borderTop: '2px solid var(--rule-strong)', paddingTop: 10, background: isHover ? 'var(--surface-sunk)' : 'transparent', outline: selected ? '1px solid var(--rule-strong)' : 'none', outlineOffset: 4, cursor: onClick ? 'pointer' : 'default', ...style }}>
      <div style={{ position: 'relative', justifySelf: 'center', opacity: muted ? 0.55 : 1 }}>
        <Portrait bot={bot} size={portraitSize} />
        {status === 'flagged' && <RubberStamp backed kind="under-investigation" size="sm" angle={-8} style={{ position: 'absolute', left: '50%', bottom: 10, transform: 'translateX(-50%)' }} />}
        {status === 'halted' && <RubberStamp backed text="Suspended" size="sm" angle={-6} style={{ position: 'absolute', left: '50%', bottom: 10, transform: 'translateX(-50%)' }} />}
      </div>
      <div>
        <div style={{ ...small, color: 'var(--ink-muted)' }}>By</div>
        <h3 style={{ margin: '1px 0 0', fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 21, lineHeight: 1.1, fontVariationSettings: '"opsz" 48' }}>{b.name}</h3>
        <div style={{ ...small, fontWeight: 600, marginTop: 3, color: 'var(--ink-muted)' }}>{b.role}</div>
      </div>
      <p style={{ margin: 0, fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 14, lineHeight: 1.35, textWrap: 'pretty' }}>{b.line}</p>
      <div style={{ fontSize: 12, fontWeight: 600, color: st.color }}><span aria-hidden="true">{st.glyph}</span> {st.label}</div>
      {stats.length > 0 && <dl style={{ margin: 0, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px 12px' }}>{stats.map((s) => <Stat key={s.label} s={s} />)}</dl>}
    </article>
  );
}
