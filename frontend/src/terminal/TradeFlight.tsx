import { useEffect, useState } from 'react'
import { type Chit, onFlight } from './flight'

/**
 * The paper trail between the order book and the trade tape.
 *
 * When trades eat a whole price level, that level's quantity has become a
 * trade — so a little chit carrying the size flies from the line in the book
 * to the top of the tape, where the trade prints a moment later. It is the one
 * step of matching that is otherwise invisible: two numbers change in two
 * different panels and nothing connects them.
 *
 * The chits are drawn once, over everything, rather than inside the book, so
 * they are not clipped by the panel they leave.
 */
export function TradeFlight() {
  const [chits, setChits] = useState<Chit[]>([])

  useEffect(() => onFlight((chit) => {
    setChits((c) => [...c, chit].slice(-6))
    setTimeout(() => setChits((c) => c.filter((x) => x.id !== chit.id)), chit.ms + 60)
  }), [])

  if (!chits.length) return null
  return (
    <div className="dx-flights" aria-hidden="true">
      {chits.map((c) => (
        <span key={c.id} className={`dx-chit is-${c.side}`} style={{
          left: c.x, top: c.y,
          ['--dx' as string]: `${c.dx}px`, ['--dy' as string]: `${c.dy}px`, ['--ms' as string]: `${c.ms}ms`,
        }}>{c.label}</span>
      ))}
    </div>
  )
}
