import './odometer.css'

const DIGITS = '0123456789'

/**
 * A number that rolls like an old mechanical scoreboard: each digit is a
 * strip 0–9 that slides to its new place, so a price change reads as motion.
 *
 * Columns are keyed from the right, so ₹999 → ₹1,000 adds a column on the
 * left without the others jumping. With reduced motion the digits simply
 * change.
 */
export function Odometer({ value, tone, className }: { value: string; tone?: 'up' | 'down'; className?: string }) {
  const chars = [...value]
  return (
    <span className={`dx-odo${tone ? ` is-${tone}` : ''}${className ? ` ${className}` : ''}`} aria-label={value} role="text">
      {chars.map((ch, i) => {
        const fromRight = chars.length - i
        const d = DIGITS.indexOf(ch)
        if (d < 0) return <span key={`s${fromRight}`} className="dx-odo-sym" aria-hidden="true">{ch}</span>
        return (
          <span key={`d${fromRight}`} className="dx-odo-col" aria-hidden="true">
            <span className="dx-odo-strip" style={{ transform: `translateY(${-d * 10}%)` }}>
              {DIGITS.split('').map((n) => <span key={n}>{n}</span>)}
            </span>
          </span>
        )
      })}
    </span>
  )
}
