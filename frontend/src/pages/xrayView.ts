import type { XRay, XRayStep } from '../lib/types'

/**
 * What the X-Ray page should show after `step` narration steps: which fills
 * have happened, how much is still to trade, and whether the stop and the
 * outcome have been reached. Pure, so the replay logic is unit-tested.
 */
export interface XRayView {
  /** Fills shown so far, in order. */
  fillsShown: number
  /** Shares still to trade at this point of the story. */
  remaining: number
  /** Shares taken from each level of bookBefore so far. */
  takenAt: Map<number, number>
  /** Numbered fills per level, e.g. level 0 → [0] means fill ① happened there. */
  fillNumbersAt: Map<number, number[]>
  stopShown: boolean
  outcomeShown: boolean
}

export function xrayView(xray: XRay, step: number): XRayView {
  const shown: XRayStep[] = xray.steps.slice(0, step + 1)
  const fillsShown = shown.filter((s) => s.kind === 'FILL').length
  const takenAt = new Map<number, number>()
  const fillNumbersAt = new Map<number, number[]>()
  xray.fills.slice(0, fillsShown).forEach((f, n) => {
    takenAt.set(f.level, (takenAt.get(f.level) ?? 0) + f.qty)
    fillNumbersAt.set(f.level, [...(fillNumbersAt.get(f.level) ?? []), n])
  })
  const filled = [...takenAt.values()].reduce((a, b) => a + b, 0)
  return {
    fillsShown,
    remaining: xray.qty - filled,
    takenAt,
    fillNumbersAt,
    stopShown: shown.some((s) => s.kind === 'STOP'),
    outcomeShown: shown.some((s) => s.kind === 'OUTCOME'),
  }
}

/** The trader with the most shares at a level of the X-Ray book. */
export function mainTraderOf(orders: { traderId: string; remainingQty: number }[]): string | undefined {
  const byTrader = new Map<string, number>()
  orders.forEach((o) => byTrader.set(o.traderId, (byTrader.get(o.traderId) ?? 0) + o.remainingQty))
  let best: string | undefined
  let most = -1
  byTrader.forEach((qty, trader) => { if (qty > most) { best = trader; most = qty } })
  return best
}

export const CIRCLED = ['①', '②', '③', '④', '⑤', '⑥', '⑦', '⑧', '⑨', '⑩']
