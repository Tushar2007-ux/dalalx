import { trailPath } from './signals'

/**
 * A stock's last few dozen trades as a thumbnail line, drawn wherever the
 * stock is named. It is scaled to its own range, so it shows the shape of the
 * move, not its size — the number beside it says how big.
 */
export function Spark({ values, width = 44, height = 14 }: { values: number[] | undefined; width?: number; height?: number }) {
  const path = values && values.length > 1 ? trailPath(values, width, height) : ''
  if (!path) return <span className="dx-spark" style={{ width, height }} aria-hidden="true" />
  const up = values![values!.length - 1] >= values![0]
  return (
    <svg className={`dx-spark ${up ? 'is-up' : 'is-down'}`} width={width} height={height}
      viewBox={`0 0 ${width} ${height}`} aria-hidden="true">
      <path d={path} />
    </svg>
  )
}
