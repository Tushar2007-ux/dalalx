import { hhmm } from '../lib/format'
import type { WireHeadline } from '../lib/types'
import { Teleprinter } from './Teleprinter'

/**
 * The Wire, news in brief: newest first. The newest headline prints itself
 * like a teleprinter; older ones are already set in type.
 */
export function WireColumn({ items, limit = 6 }: { items: WireHeadline[]; limit?: number }) {
  return (
    <ol aria-label="The Wire" className="dx-wire">
      {items.slice(0, limit).map((h, i) => {
        const glyph = h.tone === 'UP' ? '▲' : h.tone === 'DOWN' ? '▼' : h.tone === 'ALERT' ? '■' : null
        const colour = h.tone === 'UP' ? 'var(--buy)' : h.tone === 'DOWN' ? 'var(--sell)' : 'var(--stamp)'
        return (
          <li key={h.id} className={i === 0 ? 'is-newest' : undefined}>
            <span className="dx-num dx-wire-time">{hhmm(new Date(h.time))}</span>
            <span className="dx-wire-text">
              {glyph && <span aria-hidden="true" style={{ color: colour, fontSize: 'var(--text-micro)', marginRight: 'var(--space-1)', verticalAlign: '0.1em' }}>{glyph}</span>}
              {i === 0 ? <Teleprinter text={h.text} /> : h.text}
            </span>
          </li>
        )
      })}
    </ol>
  )
}
