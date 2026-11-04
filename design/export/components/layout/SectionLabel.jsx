import React from 'react';

const RULES = {
  bold: '2px solid var(--rule-strong)',
  hair: '1px solid var(--rule)',
  double: '4px double var(--rule-strong)',
  none: 'none',
};

/* Bold rule above, label in caps (13px), thin double rule beneath — the newspaper section head. */
export function SectionLabel({ children, meta, count, rule = 'bold', under = 'double', as = 'h2', id, style }) {
  const Tag = as;
  return (
    <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 12, borderTop: RULES[rule], borderBottom: under === 'double' ? '3px double var(--rule-strong)' : under === 'hair' ? '1px solid var(--rule)' : 'none', padding: '6px 0 5px', marginBottom: under === 'none' ? 0 : 4, ...style }}>
      <Tag id={id} style={{ margin: 0, fontFamily: 'var(--font-ui)', fontSize: 'var(--text-section)', fontWeight: 700, letterSpacing: '0.12em', textTransform: 'uppercase', color: 'var(--ink)' }}>
        {children}
        {count != null && <span className="dx-num" style={{ fontWeight: 500, color: 'var(--ink-muted)', letterSpacing: 0 }}> · {count}</span>}
      </Tag>
      {meta && <span style={{ fontSize: 'var(--text-small)', color: 'var(--ink-muted)' }}>{meta}</span>}
    </div>
  );
}
