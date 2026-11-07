import { useEffect, useState } from 'react'

/** Characters printed per second. */
const CPS = 55

/**
 * Text that prints itself like a teleprinter: one character at a time with a
 * blinking carriage, then the carriage disappears. A new `text` starts over.
 * With reduced motion the whole line appears at once.
 */
export function Teleprinter({ text, className }: { text: string; className?: string }) {
  const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches
  const [shown, setShown] = useState(reduced ? text.length : 0)
  useEffect(() => {
    if (reduced) { setShown(text.length); return }
    setShown(0)
    const started = performance.now()
    let frame = 0
    const tick = () => {
      const n = Math.min(text.length, Math.floor(((performance.now() - started) / 1000) * CPS))
      setShown(n)
      if (n < text.length) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [text, reduced])
  const done = shown >= text.length
  return (
    <span className={className} aria-label={text}>
      <span aria-hidden="true">{text.slice(0, shown)}</span>
      {!done && <span className="dx-caret" aria-hidden="true">▍</span>}
    </span>
  )
}
