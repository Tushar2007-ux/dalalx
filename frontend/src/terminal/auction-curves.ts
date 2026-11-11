import type { Level } from '../lib/types'

/** One point on the auction chart: at this price, how many shares buyers and sellers would trade. */
export interface CurvePoint {
  price: number
  /** Shares bid at this price or higher (buyers happy to pay at least this). */
  demand: number
  /** Shares offered at this price or lower (sellers happy to take this). */
  supply: number
}

/**
 * The demand and supply curves of a call auction, exactly as the Java
 * CallAuction defines them: for each price p, D(p) = shares bid at p or
 * above and S(p) = shares offered at p or below. The auction price is where
 * min(D, S), the shares that could actually trade, is largest.
 */
export function auctionCurves(bids: Level[], asks: Level[]): CurvePoint[] {
  const prices = [...new Set([...bids, ...asks].map((l) => l.price))].sort((a, b) => a - b)
  return prices.map((price) => ({
    price,
    demand: bids.filter((l) => l.price >= price).reduce((a, l) => a + l.totalQty, 0),
    supply: asks.filter((l) => l.price <= price).reduce((a, l) => a + l.totalQty, 0),
  }))
}

/** @return the point where the most shares would trade (ties: the smaller imbalance), or null if nothing crosses */
export function bestCross(points: CurvePoint[]): CurvePoint | null {
  let best: CurvePoint | null = null
  for (const p of points) {
    const vol = Math.min(p.demand, p.supply)
    if (vol <= 0) continue
    const bestVol = best ? Math.min(best.demand, best.supply) : 0
    if (vol > bestVol || (vol === bestVol && best && Math.abs(p.demand - p.supply) < Math.abs(best.demand - best.supply))) best = p
  }
  return best
}
