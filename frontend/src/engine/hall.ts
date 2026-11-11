/**
 * The arithmetic behind the print hall.
 *
 * Kept apart from the three.js that draws it, so the things that decide how
 * the hall behaves — how fast a press strokes, how full a bin looks, how much
 * paper is in the air — can be checked without a GPU.
 */
import type { WorkerLoad } from '../lib/types'

/** Where a lane stands, left to right, with the hall centred on zero. */
export function laneX(index: number, count: number, gap = 2.3): number {
  return (index - (count - 1) / 2) * gap
}

/**
 * Strokes per second for a press.
 *
 * A worker doing hundreds of commands a second cannot be drawn stroke for
 * stroke — nothing would be visible but a blur — so the rate is compressed
 * logarithmically: idle presses rest, busy ones hammer, and the difference
 * between 10/s and 1,000/s still reads.
 */
export function strokesPerSecond(perSecond: number): number {
  if (perSecond <= 0) return 0
  return Math.min(6, 0.6 + Math.log10(perSecond + 1) * 1.6)
}

/** How full the bin looks, on a curve so a small queue is still visible. */
export function binFill(pending: number, capacity: number): number {
  if (capacity <= 0 || pending <= 0) return 0
  return Math.min(1, Math.pow(pending / capacity, 0.35))
}

/** How many slips a second should be launched down a lane's belt. */
export function slipRate(worker: Pick<WorkerLoad, 'perSecond' | 'pending'>): number {
  const busy = worker.perSecond + worker.pending * 0.05
  if (busy <= 0) return 0
  return Math.min(14, 0.8 + Math.log10(busy + 1) * 5)
}

/** The height of the output stack, which grows with work done but never off screen. */
export function trayHeight(processed: number): number {
  return Math.min(0.9, Math.log10(processed + 1) * 0.16)
}

/** Shares a fixed budget of paper between the lanes that are actually busy. */
export function shareBudget(rates: number[], budget: number): number[] {
  const total = rates.reduce((a, r) => a + r, 0)
  if (total <= 0) return rates.map(() => 0)
  return rates.map((r) => Math.max(0, Math.round((r / total) * budget)))
}
