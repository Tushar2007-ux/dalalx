/**
 * A tiny P&L line for a correspondent's card: the last couple of minutes of
 * readings, scaled to their own range (the zero line is dotted when it's in
 * view), green while in profit and red while at a loss.
 */
export function Sparkline({ values, width = 300, height = 36 }: { values: number[]; width?: number; height?: number }) {
  if (values.length < 2) {
    return <div style={{ height, fontSize: 'var(--text-label)', color: 'var(--ink-muted)', display: 'grid', alignItems: 'center' }}>Gathering P&amp;L…</div>
  }
  // Scale to the readings' own range so the trend is visible; show the zero line only if it's in view.
  const lo = Math.min(...values)
  const hi = Math.max(...values)
  const span = hi - lo || 1
  const zeroInView = lo <= 0 && hi >= 0
  const x = (i: number) => (i / (values.length - 1)) * width
  const y = (v: number) => 3 + (1 - (v - lo) / span) * (height - 6)
  const path = values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(v).toFixed(1)}`).join(' ')
  const last = values[values.length - 1]
  const colour = last >= 0 ? 'var(--buy)' : 'var(--sell)'
  return (
    <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" width="100%" height={height} role="img"
      aria-label={`P&L trend over the last ${values.length} readings`} style={{ display: 'block' }}>
      {zeroInView && <line x1="0" x2={width} y1={y(0)} y2={y(0)} stroke="var(--rule-strong)" strokeDasharray="2 3" vectorEffect="non-scaling-stroke" />}
      <path d={`${path} L${width} ${height} L0 ${height} Z`} fill={colour} opacity="0.1" />
      <path d={path} fill="none" stroke={colour} strokeWidth="1.6" vectorEffect="non-scaling-stroke" />
      <circle cx={x(values.length - 1)} cy={y(last)} r="2.6" fill={colour} />
    </svg>
  )
}

/** Appends a reading to a bounded history (pure, for tests). */
export function pushReading(history: number[], value: number, max = 60): number[] {
  return [...history, value].slice(-max)
}
