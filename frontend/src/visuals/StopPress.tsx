/**
 * STOP PRESS: the black bar a newspaper runs when news arrives after the
 * page was set. It drops in above the halt notice the moment a circuit
 * breaker trips, while the price ticker above it rolls to a standstill —
 * the presses have stopped.
 */
export function StopPress({ symbol }: { symbol: string }) {
  const line = `Trading in ${symbol} halted · Circuit breaker tripped · The presses have stopped`
  return (
    <div className="dx-stoppress" role="alert" key={symbol}>
      <span className="dx-stoppress-flag">Stop press</span>
      <span className="dx-stoppress-run" aria-label={line}>
        <span aria-hidden="true">{line} · {line} · </span>
      </span>
      <span className="dx-stoppress-flag is-end" aria-hidden="true">Stop press</span>
    </div>
  )
}
