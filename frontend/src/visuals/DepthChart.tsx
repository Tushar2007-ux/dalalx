import { formatPaise, formatQty } from '../lib/format'
import type { BookSnapshot } from '../lib/types'
import { depthCurve } from './signals'

const W = 300
const H = 92

/**
 * The order book drawn as two staircases: cumulative bids climbing to the
 * left, cumulative asks to the right, meeting at the spread.
 *
 * The table above shows each level; this shows the wall behind it. A market
 * order's cost is the area it has to climb, so a steep side is a thin side.
 * Steps, not a smooth curve: depth really does change in jumps, at prices.
 */
export function DepthChart({ book }: { book: BookSnapshot | undefined }) {
  const bids = depthCurve(book?.bids ?? [])
  const asks = depthCurve(book?.asks ?? [])
  if (!bids.length || !asks.length) return null

  const lo = bids[bids.length - 1].price
  const hi = asks[asks.length - 1].price
  const span = hi - lo || 1
  const top = Math.max(bids[bids.length - 1].total, asks[asks.length - 1].total) || 1
  const x = (price: number) => ((price - lo) / span) * W
  const y = (total: number) => H - (total / top) * (H - 8)

  // Each side is a staircase from its best price outward, closed down to the axis.
  const stairs = (points: typeof bids) => {
    let d = `M${x(points[0].price).toFixed(1)} ${H}`
    points.forEach((p, i) => {
      d += ` L${x(p.price).toFixed(1)} ${y(p.total).toFixed(1)}`
      const next = points[i + 1]
      if (next) d += ` L${x(next.price).toFixed(1)} ${y(p.total).toFixed(1)}`
    })
    return `${d} L${x(points[points.length - 1].price).toFixed(1)} ${H} Z`
  }
  const mid = (bids[0].price + asks[0].price) / 2

  return (
    <figure className="dx-depthchart">
      <figcaption>
        <span>Depth</span>
        <span className="dx-num">{formatQty(bids[bids.length - 1].total)} bid · {formatQty(asks[asks.length - 1].total)} offered</span>
      </figcaption>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img"
        aria-label={`Depth chart: ${formatQty(bids[bids.length - 1].total)} shares bid, ${formatQty(asks[asks.length - 1].total)} offered`}>
        <path className="dx-depthchart-bid" d={stairs(bids)} vectorEffect="non-scaling-stroke" />
        <path className="dx-depthchart-ask" d={stairs(asks)} vectorEffect="non-scaling-stroke" />
        <line className="dx-depthchart-mid" x1={x(mid)} x2={x(mid)} y1="0" y2={H} vectorEffect="non-scaling-stroke" />
      </svg>
      <div className="dx-depthchart-axis dx-num">
        <span>₹{formatPaise(lo)}</span><span>mid ₹{formatPaise(Math.round(mid))}</span><span>₹{formatPaise(hi)}</span>
      </div>
    </figure>
  )
}
