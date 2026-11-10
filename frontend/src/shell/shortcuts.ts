import { useEffect } from 'react'
import { useMarket } from '../store/market'

/** The keyboard shortcuts, for the help card and for tests. */
export const SHORTCUTS: { keys: string; what: string }[] = [
  { keys: 'B', what: 'Ticket: Buy side' },
  { keys: 'S', what: 'Ticket: Sell side' },
  { keys: '1 – 5', what: 'Pick a stock' },
  { keys: 'V', what: 'Viva mode on/off' },
  { keys: 'P', what: 'Presenter mode on/off' },
  { keys: 'N', what: 'Day / Night edition' },
  { keys: 'M', what: 'Sound on/off' },
  { keys: 'F', what: 'Trading Floor' },
  { keys: 'E', what: 'Engine Room' },
  { keys: 'W', what: 'Big Board' },
  { keys: 'A', what: 'Audience' },
  { keys: '?', what: 'This card' },
  { keys: 'Esc', what: 'Close cards and big moments' },
]

const PAGES: Record<string, string> = { f: '#/floor', e: '#/engine', w: '#/wall', a: '#/audience' }

/** True when the key press is meant for a text field, not for us. */
export function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  return target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
}

/**
 * Decides what a key does. Pure (it only returns a description), so it can be
 * tested without a browser.
 */
export function actionFor(key: string, symbols: string[]):
  | { kind: 'side'; side: 'buy' | 'sell' } | { kind: 'select'; symbol: string } | { kind: 'toggle'; what: 'viva' | 'presenter' | 'edition' | 'help' | 'sound' }
  | { kind: 'go'; hash: string } | { kind: 'escape' } | null {
  const k = key.length === 1 ? key.toLowerCase() : key
  if (k === 'b') return { kind: 'side', side: 'buy' }
  if (k === 's') return { kind: 'side', side: 'sell' }
  if (k === 'v') return { kind: 'toggle', what: 'viva' }
  if (k === 'p') return { kind: 'toggle', what: 'presenter' }
  if (k === 'n') return { kind: 'toggle', what: 'edition' }
  if (k === 'm') return { kind: 'toggle', what: 'sound' }
  if (k === '?') return { kind: 'toggle', what: 'help' }
  if (k === 'Escape') return { kind: 'escape' }
  if (PAGES[k]) return { kind: 'go', hash: PAGES[k] }
  if (/^[1-9]$/.test(k) && symbols[Number(k) - 1]) return { kind: 'select', symbol: symbols[Number(k) - 1] }
  return null
}

/** Installs the shortcuts for the whole app (not on the phone page, where there is no keyboard to speak of). */
export function useShortcuts(enabled: boolean): void {
  useEffect(() => {
    if (!enabled) return
    const onKey = (e: KeyboardEvent) => {
      if (e.metaKey || e.ctrlKey || e.altKey || isTyping(e.target)) return
      const s = useMarket.getState()
      const action = actionFor(e.key, s.symbols.map((x) => x.symbol))
      if (!action) return
      switch (action.kind) {
        case 'side':
          if (location.hash !== '#/floor') location.hash = '#/floor'
          s.chooseSide(action.side)
          break
        case 'select': s.select(action.symbol); break
        case 'go': location.hash = action.hash; break
        case 'escape': s.setHelpOpen(false); break
        case 'toggle':
          if (action.what === 'viva') s.setViva(!s.viva)
          else if (action.what === 'presenter') s.setPresenter(!s.presenter)
          else if (action.what === 'edition') s.setEdition(s.edition === 'day' ? 'night' : 'day')
          else if (action.what === 'sound') s.setSound(!s.sound)
          else s.setHelpOpen(!s.helpOpen)
          break
      }
      e.preventDefault()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [enabled])
}
