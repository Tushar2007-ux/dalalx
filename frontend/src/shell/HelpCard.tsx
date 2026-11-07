import { useMarket } from '../store/market'
import { SHORTCUTS } from './shortcuts'

/** The keyboard-shortcut card (press ?). */
export function HelpCard() {
  const open = useMarket((s) => s.helpOpen)
  const close = useMarket((s) => s.setHelpOpen)
  if (!open) return null
  return (
    <div className="dx-help" role="dialog" aria-label="Keyboard shortcuts" onClick={() => close(false)}>
      <div className="dx-help-card" onClick={(e) => e.stopPropagation()}>
        <div className="dx-help-title">Keyboard shortcuts</div>
        <dl>
          {SHORTCUTS.map((s) => (
            <div key={s.keys}><dt><kbd>{s.keys}</kbd></dt><dd>{s.what}</dd></div>
          ))}
        </dl>
        <button type="button" className="dx-focus" onClick={() => close(false)}>Close</button>
      </div>
    </div>
  )
}
