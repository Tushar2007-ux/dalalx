import React from 'react';

/* A hand-drawn loop that overshoots its start, like a pencil circle around a suspicious figure. */
const LOOP = 'M 22 46 C 16 20, 70 6, 116 8 C 164 10, 196 26, 190 46 C 184 68, 128 76, 86 73 C 42 70, 6 60, 12 40 C 16 27, 44 15, 74 12';

export function RedPencilCircle({ children, draw = true, drawKey, color = 'var(--pencil)', label, style }) {
  return (
    <span style={{ position: 'relative', display: 'inline-block', padding: '2px 8px', ...style }}>
      {children}
      <svg aria-hidden="true" viewBox="0 0 200 80" preserveAspectRatio="none" style={{ position: 'absolute', left: -6, top: -7, width: 'calc(100% + 12px)', height: 'calc(100% + 14px)', overflow: 'visible', pointerEvents: 'none' }}>
        <path key={drawKey} d={LOOP} pathLength="100" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" vectorEffect="non-scaling-stroke"
          strokeDasharray="100" strokeDashoffset={draw ? 100 : 0} className={draw ? 'dx-anim-draw' : undefined} />
      </svg>
      {label && <span style={{ position: 'absolute', clip: 'rect(0 0 0 0)', width: 1, height: 1, overflow: 'hidden' }}>{label}</span>}
    </span>
  );
}
