/**
 * The arithmetic behind the newer visuals: sparklines, the depth chart, the
 * market map, the weather box, order-book heat and the bots' expressions.
 *
 * Every function here is pure — numbers in, numbers out — and none of them
 * touches the page. The components that draw these things stay thin, and the
 * rules that decide what they show can be read and tested on their own.
 */
import type { Level, WireHeadline } from '../lib/types'

/* ---- Price trails (sparklines) ------------------------------------------ */

/** Most points a trail keeps: enough for a shape, few enough to stay cheap. */
export const TRAIL_SIZE = 48

/** Adds a price to a stock's recent trail, dropping the oldest when full. */
export function pushPrice(trail: number[] | undefined, price: number, max = TRAIL_SIZE): number[] {
  const next = trail ? [...trail, price] : [price]
  return next.length > max ? next.slice(next.length - max) : next
}

/** An SVG path for a trail, scaled to its own range inside a w × h box. */
export function trailPath(values: number[], w: number, h: number, pad = 1.5): string {
  if (values.length < 2) return ''
  const lo = Math.min(...values)
  const span = Math.max(...values) - lo || 1
  return values
    .map((v, i) => {
      const x = (i / (values.length - 1)) * w
      const y = pad + (1 - (v - lo) / span) * (h - pad * 2)
      return `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`
    })
    .join(' ')
}

/* ---- My fills on the chart ------------------------------------------------ */

export interface MyFill { at: number; price: number; side: 'BUY' | 'SELL'; qty: number }
export interface FillMark { index: number; price: number; side: 'BUY' | 'SELL'; qty: number }

/** Most of my fills a stock remembers for its chart. */
export const FILLS_KEPT = 60

/** Remembers one more of my fills, dropping the oldest when full. */
export function pushFill(fills: MyFill[] | undefined, fill: MyFill, max = FILLS_KEPT): MyFill[] {
  const next = fills ? [...fills, fill] : [fill]
  return next.length > max ? next.slice(next.length - max) : next
}

/**
 * Places my fills on the candles they happened in: the last candle that had
 * opened by then. One order often fills in several trades, so fills on the
 * same candle and the same side are merged into one mark at their average
 * price (weighted by size). A fill from before the first candle on screen has
 * no candle to sit on and is left off. `starts` are the candles' opening
 * times, oldest first.
 */
export function fillMarks(starts: number[], fills: MyFill[]): FillMark[] {
  const merged = new Map<string, { index: number; side: MyFill['side']; qty: number; value: number }>()
  for (const f of fills) {
    let index = -1
    for (let i = 0; i < starts.length && starts[i] <= f.at; i++) index = i
    if (index < 0) continue
    const key = `${index}:${f.side}`
    const mark = merged.get(key) ?? { index, side: f.side, qty: 0, value: 0 }
    mark.qty += f.qty
    mark.value += f.price * f.qty
    merged.set(key, mark)
  }
  return [...merged.values()].map((m) => ({ index: m.index, side: m.side, qty: m.qty, price: Math.round(m.value / m.qty) }))
}

/* ---- Depth chart --------------------------------------------------------- */

export interface DepthPoint { price: number; total: number }

/**
 * Cumulative depth on one side of the book: how many shares you could trade
 * if you walked the price out to each level. `levels` must be best-first,
 * which is how the book already arrives.
 */
export function depthCurve(levels: Pick<Level, 'price' | 'totalQty'>[]): DepthPoint[] {
  let total = 0
  return levels.map((l) => ({ price: l.price, total: (total += l.totalQty) }))
}

/* ---- Market map (treemap) ------------------------------------------------ */

export interface Tile { key: string; x: number; y: number; w: number; h: number }

/**
 * Lays weighted tiles into a rectangle, largest first, splitting whichever
 * side of the remaining space is longer. Simple "slice and dice" — with five
 * stocks it gives well-shaped tiles without the full squarify algorithm.
 */
export function layoutTiles(items: { key: string; weight: number }[], width: number, height: number): Tile[] {
  const sorted = [...items].filter((i) => i.weight > 0).sort((a, b) => b.weight - a.weight)
  const tiles: Tile[] = []
  let x = 0, y = 0, w = width, h = height
  let remaining = sorted.reduce((sum, i) => sum + i.weight, 0)
  sorted.forEach((item, index) => {
    const share = item.weight / remaining
    const last = index === sorted.length - 1
    if (w >= h) {
      const tw = last ? w : w * share
      tiles.push({ key: item.key, x, y, w: tw, h })
      x += tw; w -= tw
    } else {
      const th = last ? h : h * share
      tiles.push({ key: item.key, x, y, w, h: th })
      y += th; h -= th
    }
    remaining -= item.weight
  })
  return tiles
}

/** How strongly a tile is inked for a move of `pct` percent (capped at ±3%). */
export function moveInk(pct: number): number {
  return Math.min(1, Math.abs(pct) / 3)
}

/* ---- Market weather ------------------------------------------------------ */

export type Weather = 'fair' | 'breezy' | 'unsettled' | 'stormy'

/**
 * Average recent swing across the stocks, as a percentage: for each trail,
 * its high-to-low range over its mean. A quiet tape scores near zero.
 */
export function swing(trails: number[][]): number {
  const usable = trails.filter((t) => t.length >= 2)
  if (!usable.length) return 0
  const ranges = usable.map((t) => {
    const mean = t.reduce((a, b) => a + b, 0) / t.length
    return mean ? ((Math.max(...t) - Math.min(...t)) / mean) * 100 : 0
  })
  return ranges.reduce((a, b) => a + b, 0) / ranges.length
}

/** The outlook the front page prints. Any halted stock means a storm. */
export function weatherFor(swingPct: number, anyHalted: boolean): Weather {
  if (anyHalted || swingPct >= 4) return 'stormy'
  if (swingPct >= 1.5) return 'unsettled'
  if (swingPct >= 0.5) return 'breezy'
  return 'fair'
}

export const WEATHER_COPY: Record<Weather, { title: string; line: string }> = {
  fair: { title: 'Fair', line: 'Light trade, narrow ranges. A day for patient limit orders.' },
  breezy: { title: 'Breezy', line: 'Prices are moving, but in order. Spreads are holding.' },
  unsettled: { title: 'Unsettled', line: 'Wide swings on the tape. Expect fills away from the last price.' },
  stormy: { title: 'Stormy', line: 'Violent moves; a circuit breaker is in play. Mind your market orders.' },
}

/* ---- Order-book heat ----------------------------------------------------- */

/** A level's warmth, 0–1, on a curve so mid-sized levels still show. */
export function levelHeat(qty: number, maxQty: number): number {
  if (qty <= 0 || maxQty <= 0) return 0
  return Math.min(1, Math.sqrt(qty / maxQty))
}

/* ---- Breaking news ------------------------------------------------------- */

/** How long an alert headline holds the red band. */
export const BREAKING_MS = 9000

/** The headline that should interrupt the wire right now, if any. */
export function breakingNow(wire: WireHeadline[], now: number): WireHeadline | null {
  const top = wire[0]
  if (!top || top.tone !== 'ALERT') return null
  return now - Date.parse(top.time) < BREAKING_MS ? top : null
}

/* ---- The correspondents' expressions ------------------------------------- */

export type BotMood = 'calm' | 'busy' | 'sweating' | 'chasing' | 'watched' | 'suspended'

/**
 * What a bot's portrait should be doing. Checked in order of seriousness:
 * a suspension outranks an investigation, which outranks the market.
 */
export function botMoodOf(bot: string, ctx: {
  suspended: boolean
  underInvestigation: boolean
  /** The selected stock's move today, in percent. */
  movePct: number
  tradedJustNow: boolean
}): BotMood {
  if (ctx.suspended) return 'suspended'
  if (ctx.underInvestigation) return 'watched'
  if (bot === 'PANIC' && ctx.movePct <= -0.8) return 'sweating'
  if (bot === 'MOMENTUM' && Math.abs(ctx.movePct) >= 0.8) return 'chasing'
  return ctx.tradedJustNow ? 'busy' : 'calm'
}
