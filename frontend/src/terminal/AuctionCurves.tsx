import { formatPaise, formatQty } from '../lib/format'
import type { AuctionUpdate, BookSnapshot } from '../lib/types'
import { auctionCurves, bestCross } from './auctionCurves'

const W = 360
const H = 150
const PAD = { l: 8, r: 8, t: 14, b: 22 }

/**
 * The call auction drawn live: the demand curve (buyers) falls as the price
 * rises, the supply curve (sellers) climbs, and the auction price is where
 * the most shares can change hands. Updates with every book snapshot.
 */
export function AuctionCurves({ book, auction }: { book?: BookSnapshot; auction?: AuctionUpdate }) {
  const points = book ? auctionCurves(book.bids, book.asks) : []
  if (points.length < 2) {
    return <p className="dx-curves-wait">The curves appear as soon as buyers and sellers place orders in the auction.</p>
  }
  const lo = points[0].price
  const hi = points[points.length - 1].price
  const maxQ = Math.max(1, ...points.map((p) => Math.max(p.demand, p.supply)))
  const x = (price: number) => PAD.l + ((price - lo) / (hi - lo || 1)) * (W - PAD.l - PAD.r)
  const y = (q: number) => PAD.t + (1 - q / maxQ) * (H - PAD.t - PAD.b)
  const step = (key: 'demand' | 'supply') => points.map((p, i) => `${i ? 'L' : 'M'}${x(p.price).toFixed(1)} ${y(p[key]).toFixed(1)}`).join(' ')
  // The exchange's own indicative price wins (it also breaks ties by nearness to the last trade);
  // the curves' own maximum is only a fallback until the first update arrives.
  const local = bestCross(points)
  const best = auction?.price != null
    ? { price: Math.min(hi, Math.max(lo, auction.price)), demand: auction.volume, supply: auction.volume }
    : local
  const traded = best ? Math.min(best.demand, best.supply) : 0

  return (
    <figure className="dx-curves" aria-label="Auction demand and supply curves">
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img">
        <line x1={PAD.l} x2={W - PAD.r} y1={H - PAD.b} y2={H - PAD.b} stroke="var(--rule-strong)" />
        {best && <rect x={PAD.l} width={x(best.price) - PAD.l} y={y(traded)} height={H - PAD.b - y(traded)} fill="var(--pencil)" opacity="0.08" />}
        <path d={step('demand')} fill="none" stroke="var(--buy)" strokeWidth="2.2" vectorEffect="non-scaling-stroke" className="dx-curves-line" />
        <path d={step('supply')} fill="none" stroke="var(--sell)" strokeWidth="2.2" vectorEffect="non-scaling-stroke" className="dx-curves-line" />
        {best && <>
          <line x1={x(best.price)} x2={x(best.price)} y1={PAD.t - 6} y2={H - PAD.b} stroke="var(--ink)" strokeDasharray="3 3" vectorEffect="non-scaling-stroke" />
          <circle cx={x(best.price)} cy={y(traded)} r="4.5" fill="var(--pencil)" stroke="var(--paper)" strokeWidth="1.5" className="dx-curves-dot" />
        </>}
        <text x={PAD.l} y={H - 6} className="dx-curves-axis">₹{formatPaise(lo)}</text>
        <text x={W - PAD.r} y={H - 6} textAnchor="end" className="dx-curves-axis">₹{formatPaise(hi)}</text>
      </svg>
      <figcaption>
        <span style={{ color: 'var(--buy)' }}>━ Demand</span> buyers at this price or higher ·{' '}
        <span style={{ color: 'var(--sell)' }}>━ Supply</span> sellers at this price or lower
        {best && <> · <b>₹{formatPaise(best.price)}</b> crosses <b>{formatQty(traded)}</b> shares</>}
      </figcaption>
    </figure>
  )
}
