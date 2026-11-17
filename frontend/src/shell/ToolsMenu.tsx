import { useEffect, useRef, useState } from 'react'
import { api } from '../lib/api'
import { useMarket } from '../store/market'

/**
 * One menu for the things that change how Uncross is shown rather than what it
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
  const three = useMarket((s) => s.three)
  const setThree = useMarket((s) => s.setThree)
  const setHelpOpen = useMarket((s) => s.setHelpOpen)
  const [open, setOpen] = useState(false)
  const [news, setNews] = useState<boolean | null>(null)
  const box = useRef<HTMLDivElement>(null)

  // The news desk's switch lives on the server; read it when the menu opens.
  useEffect(() => {
    if (open) api.newsDesk().then((d) => setNews(d.enabled)).catch(() => setNews(null))
  }, [open])

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
          <Item label="Sound" hint="Bell, breaker, stamp, typewriter and news chime" shortcut="M"
            checked={sound} onClick={() => setSound(!sound)} />
          <Item label="3D" hint="Press intro, engine room and price hero" shortcut="D"
            checked={three} onClick={() => setThree(!three)} />
          {news !== null && <Item label="Company news" hint="A simulated story every minute or two" shortcut=""
            checked={news} onClick={() => api.setNewsDesk(!news).then((d) => setNews(d.enabled)).catch(() => {})} />}
          <button type="button" role="menuitem" className="dx-tools-item dx-focus"
            onClick={() => { setOpen(false); void api.breakNews().catch(() => {}) }}>
            <span className="dx-tools-box" aria-hidden="true">✎</span>
            <span className="dx-tools-text">Break a story now<small>Good or bad news for one stock</small></span>
            <kbd />
          </button>
          <a role="menuitem" className="dx-tools-item dx-focus" href="#/rewind" onClick={() => setOpen(false)}>
            <span className="dx-tools-box" aria-hidden="true">⟲</span>
            <span className="dx-tools-text">Rewind<small>Play a saved session back</small></span>
            <kbd />
          </a>
          <a role="menuitem" className="dx-tools-item dx-focus" href="#/edition" onClick={() => setOpen(false)}>
            <span className="dx-tools-box" aria-hidden="true">▤</span>
            <span className="dx-tools-text">Evening edition<small>Today’s paper, ready to print</small></span>
            <kbd />
          </a>
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
