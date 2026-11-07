import type { Level } from '../lib/types'

/**
 * What happened to one side of the book between two snapshots, so the panel
 * can show matching as it happens: levels that were partly eaten (and by how
 * much) and levels that were eaten completely.
 */
export interface SideChanges {
  /** price → share of the level that disappeared, 0–1. */
  hits: Map<number, number>
  /** Levels that were in the old book but not the new one. */
  gone: Level[]
}

/**
 * Compares one side of the book before and after.
 *
 * Only levels at or inside the new best price count as "gone": those are the
 * ones trades ate through. A level that left from deep in the book was
 * cancelled, not traded, and simply disappears.
 */
export function sideChanges(before: Level[] | undefined, after: Level[], side: 'bid' | 'ask'): SideChanges {
  const hits = new Map<number, number>()
  const gone: Level[] = []
  if (!before) return { hits, gone }
  const now = new Map(after.map((l) => [l.price, l]))
  const best = after[0]?.price
  for (const old of before) {
    const cur = now.get(old.price)
    if (cur) {
      if (cur.totalQty < old.totalQty) hits.set(old.price, (old.totalQty - cur.totalQty) / old.totalQty)
    } else {
      const inside = best === undefined || (side === 'bid' ? old.price >= best : old.price <= best)
      if (inside) gone.push(old)
    }
  }
  return { hits, gone }
}
