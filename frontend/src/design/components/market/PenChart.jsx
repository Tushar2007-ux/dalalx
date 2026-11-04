import React from 'react';

const fmt = (n) => Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export function PenChart({ data = [], prevClose, width: fixedWidth, height = 140, animate = true, style }) {
  const ref = React.useRef(null);
  const [measured, setMeasured] = React.useState(360);
  React.useEffect(() => {
    if (fixedWidth || !ref.current || typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver((e) => setMeasured(Math.max(160, Math.floor(e[0].contentRect.width))));
    ro.observe(ref.current);
    return () => ro.disconnect();
  }, [fixedWidth]);
  const width = fixedWidth || measured;
  if (data.length < 2) return <div ref={ref} style={{ width: fixedWidth || '100%', height }} />;
  const pad = { t: 8, r: 64, b: 8, l: 0 };
  const vals = prevClose != null ? data.concat(prevClose) : data;
  const min = Math.min(...vals), max = Math.max(...vals), span = max - min || 1;
  const w = width - pad.l - pad.r, h = height - pad.t - pad.b;
  const X = (i) => pad.l + (i / (data.length - 1)) * w;
  const Y = (v) => pad.t + (1 - (v - min) / span) * h;
  const pts = data.map((v, i) => [X(i), Y(v)]);
  const head = pts.slice(0, -1).map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' ');
  const a = pts[pts.length - 2], b = pts[pts.length - 1];
  const segLen = Math.hypot(b[0] - a[0], b[1] - a[1]);
  const last = data[data.length - 1];
  const up = prevClose == null || last >= prevClose;
  return (
    <div ref={ref} style={{ position: 'relative', width: fixedWidth || '100%', height, ...style }}>
      <svg width={width} height={height} viewBox={'0 0 ' + width + ' ' + height} role="img" aria-label={'Intraday price, last ' + fmt(last)} style={{ display: 'block', overflow: 'visible' }}>
        {[0.25, 0.5, 0.75].map((f) => <line key={f} x1={0} x2={w} y1={pad.t + f * h} y2={pad.t + f * h} stroke="var(--rule)" strokeWidth="1" />)}
        {prevClose != null && <line x1={0} x2={w} y1={Y(prevClose)} y2={Y(prevClose)} stroke="var(--ink-muted)" strokeWidth="1" strokeDasharray="2 3" />}
        <path d={head} fill="none" stroke="var(--ink)" strokeWidth="1.5" strokeLinejoin="round" strokeLinecap="round" />
        <line key={data.length} x1={a[0]} y1={a[1]} x2={b[0]} y2={b[1]} stroke="var(--ink)" strokeWidth="1.5" strokeLinecap="round"
          className={animate ? 'dx-anim-pen' : undefined} strokeDasharray={segLen} strokeDashoffset={animate ? segLen : 0} />
        <rect x={b[0] - 2.5} y={b[1] - 2.5} width="5" height="5" fill="var(--ink)" />
      </svg>
      <div className="dx-num" style={{ position: 'absolute', left: w + 8, top: b[1] - 9, fontSize: 12, fontWeight: 600, color: up ? 'var(--buy)' : 'var(--sell)', whiteSpace: 'nowrap' }}>
        {up ? '▲' : '▼'} {fmt(last)}
      </div>
      {prevClose != null && Math.abs(Y(prevClose) - b[1]) > 16 && (
        <div className="dx-num" style={{ position: 'absolute', left: w + 8, top: Y(prevClose) - 8, fontSize: 10.5, color: 'var(--ink-muted)', whiteSpace: 'nowrap' }}>prev {fmt(prevClose)}</div>
      )}
    </div>
  );
}
