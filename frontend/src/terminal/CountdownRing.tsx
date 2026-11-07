import { useEffect, useState } from 'react'

/**
 * A ring that drains as a halt runs out, like a kitchen timer. The number in
 * the middle is the seconds left; the ring turns red in the last five.
 */
export function CountdownRing({ endsAt, total = 30_000, label }: { endsAt: number; total?: number; label: string }) {
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 100)
    return () => clearInterval(t)
  }, [])
  const left = Math.max(0, endsAt - now)
  const share = Math.min(1, left / total)
  const r = 42
  const c = 2 * Math.PI * r
  const final = left <= 5_000
  return (
    <div className={`dx-ring${final ? ' is-final' : ''}`} role="timer" aria-label={`${label}: ${Math.ceil(left / 1000)} seconds`}>
      <svg viewBox="0 0 100 100" width="104" height="104">
        <circle cx="50" cy="50" r={r} fill="none" stroke="var(--rule)" strokeWidth="7" />
        <circle cx="50" cy="50" r={r} fill="none" stroke={final ? 'var(--stamp)' : 'var(--ink)'} strokeWidth="7"
          strokeDasharray={c} strokeDashoffset={c * (1 - share)} transform="rotate(-90 50 50)" strokeLinecap="butt" />
      </svg>
      <span className="dx-ring-num">{Math.ceil(left / 1000)}</span>
      <span className="dx-ring-label">{label}</span>
    </div>
  )
}
