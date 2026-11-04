import React from 'react';

export function Edition({ edition = 'day', grain = true, motion, children, style, ...rest }) {
  return (
    <div
      data-edition={edition}
      data-motion={motion}
      className={grain ? 'dx-paper' : undefined}
      style={{ background: 'var(--paper)', color: 'var(--ink)', fontFamily: 'var(--font-ui)', fontSize: 'var(--text-body)', lineHeight: 'var(--leading-body)', ...style }}
      {...rest}
    >
      {children}
    </div>
  );
}
