import { useState } from 'react'
import { useMarket } from '../store/market'
import { VIVA_NOTES, type VivaKey } from './notes'
import './viva.css'

/**
 * A red-pencil margin note naming the Java class behind a part of the screen.
 * Hidden unless Viva mode is on. Click it to read two lines you can say to an
 * examiner.
 */
export function VivaTag({ note, align = 'right' }: { note: VivaKey; align?: 'left' | 'right' }) {
  const on = useMarket((s) => s.viva)
  const [open, setOpen] = useState(false)
  if (!on) return null
  const n = VIVA_NOTES[note]
  return (
    <span className={`dx-viva dx-viva-${align}${open ? ' is-open' : ''}`}>
      <button type="button" className="dx-viva-tag dx-focus" aria-expanded={open} onClick={() => setOpen(!open)}
        title="What's behind this? (Viva mode)">
        <em>{n.idea}</em>
        <code>{n.cls}</code>
      </button>
      {open && (
        <span className="dx-viva-card" role="note">
          <b>{n.idea}</b>
          {n.say}
        </span>
      )}
    </span>
  )
}
