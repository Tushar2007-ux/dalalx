import { useEffect, useRef, useState } from 'react'
import { useMarket } from '../store/market'

/**
 * One menu for the things that change how DalalX is shown rather than what it
 * shows: Viva mode, Presenter mode, sound and the shortcut card.
 *
 * They used to sit as four separate buttons in the section bar, which left no
 * room for the pages themselves. Behind one button the bar stays legible, and
 * the keyboard shortcut for each is printed beside it so people learn them.
 */
export function ToolsMenu() {
  const viva = useMarket((s) => s.viva)
  const setViva = useMarket((s) => s.setViva)
  const presenter = useMarket((s) => s.presenter)
  const setPresenter = useMarket((s) => s.setPresenter)
  const sound = useMarket((s) => s.sound)
  const setSound = useMarket((s) => s.setSound)
  const setHelpOpen = useMarket((s) => s.setHelpOpen)
  const [open, setOpen] = useState(false)
  const box = useRef<HTMLDivElement>(null)

  // Clicking elsewhere, or pressing Escape, closes the menu.
  useEffect(() => {
    if (!open) return
    const away = (e: MouseEvent) => { if (!box.current?.contains(e.target as Node)) setOpen(false) }
    const key = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', away)
    document.addEventListener('keydown', key)
    return () => { document.removeEventListener('mousedown', away); document.removeEventListener('keydown', key) }
  }, [open])

  const on = viva || presenter
  return (
    <div className="dx-tools" ref={box}>
      <button type="button" className={`dx-focus dx-mode${on ? ' is-on' : ''}`} aria-expanded={open} aria-haspopup="menu"
        onClick={() => setOpen(!open)} title="Viva mode, presenter mode, sound and shortcuts">
        Tools{on && <span className="dx-tools-dot" aria-label=", some are on" />}
      </button>
      {open && (
        <div className="dx-tools-menu" role="menu">
          <Item label="Viva mode" hint="Names the Java class behind each panel" shortcut="V"
            checked={viva} onClick={() => setViva(!viva)} />
          <Item label="Presenter mode" hint="Bigger type for a projector" shortcut="P"
            checked={presenter} onClick={() => setPresenter(!presenter)} />
          <Item label="Sound" hint="Bell, circuit breaker and stamp" shortcut="M"
            checked={sound} onClick={() => setSound(!sound)} />
          <button type="button" role="menuitem" className="dx-tools-item dx-focus"
            onClick={() => { setOpen(false); setHelpOpen(true) }}>
            <span className="dx-tools-box" aria-hidden="true" />
            <span className="dx-tools-text">Keyboard shortcuts<small>Everything you can press</small></span>
            <kbd>?</kbd>
          </button>
        </div>
      )}
    </div>
  )
}

function Item({ label, hint, shortcut, checked, onClick }:
  { label: string; hint: string; shortcut: string; checked: boolean; onClick: () => void }) {
  return (
    <button type="button" role="menuitemcheckbox" aria-checked={checked} className="dx-tools-item dx-focus" onClick={onClick}>
      <span className={`dx-tools-box${checked ? ' is-on' : ''}`} aria-hidden="true">{checked ? '✓' : ''}</span>
      <span className="dx-tools-text">{label}<small>{hint}</small></span>
      <kbd>{shortcut}</kbd>
    </button>
  )
}
