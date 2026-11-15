import { useEffect, useRef, useState } from 'react'
import { useMarket } from '../store/market'

/**
 * A roller of ink that crosses the screen whenever the page changes, so a
 * new page looks freshly printed. It never plays on the first page (the
 * intro owns that moment), never blocks a click, and is skipped entirely
 * for people who prefer reduced motion.
 */
export function InkWipe({ route }: { route: string }) {
  // The roller sits outside the page, so it is told which edition's ink to use.
  const edition = useMarket((s) => s.edition)
  const first = useRef(route)
  const [moved, setMoved] = useState(false)
  useEffect(() => {
    if (route !== first.current) setMoved(true)
  }, [route])
  if (!moved) return null
  return <div key={route} data-edition={edition} className="dx-inkwipe" aria-hidden="true"><span /></div>
}
