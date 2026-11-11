/**
 * Pure helpers for the Big Board's tiles (kept apart from React for testing).
 */

/** Moves this big (percent) or more glow at full strength. */
export const FULL_GLOW_PCT = 3

/**
 * How a tile should glow for a day's change: its direction and a strength
 * from 0.15 (barely moved) to 1 (moved {@link FULL_GLOW_PCT}% or more).
 */
export function boardTone(pct: number): { direction: 'up' | 'down' | 'flat'; strength: number } {
  if (Math.abs(pct) < 0.005) return { direction: 'flat', strength: 0.15 }
  const strength = 0.15 + 0.85 * Math.min(1, Math.abs(pct) / FULL_GLOW_PCT)
  return { direction: pct > 0 ? 'up' : 'down', strength }
}

/**
 * An SVG path for a sparkline of prices, scaled to the box. With `closed`,
 * the path is closed along the bottom edge, for the soft fill under the line.
 */
export function sparkPath(prices: number[], width: number, height: number, closed: boolean): string {
  if (prices.length < 2) return ''
  const lo = Math.min(...prices)
  const hi = Math.max(...prices)
  const span = hi - lo || 1
  const x = (i: number) => (i / (prices.length - 1)) * width
  const y = (p: number) => 3 + (1 - (p - lo) / span) * (height - 6)
  const line = prices.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(p).toFixed(1)}`).join(' ')
  return closed ? `${line} L${width} ${height} L0 ${height} Z` : line
}
