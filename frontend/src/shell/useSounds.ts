import { useEffect } from 'react'
import { effectFor, effectForHeadline, play, typewriterDue, unlockSoundOnFirstGesture } from '../lib/sound'
import { useMarket } from '../store/market'

/** Plays the effects for big moments and for your own fills, if sound is on. */
export function useSounds(): void {
  useEffect(() => {
    unlockSoundOnFirstGesture()
    let lastType = 0
    return useMarket.subscribe((s, prev) => {
      if (!s.sound) return
      // The teleprinter: one clack when a new trade prints on the selected stock's tape.
      const head = s.tapes[s.selected]?.[0]
      if (head && head !== prev.tapes[s.selected]?.[0] && location.hash === '#/floor' && typewriterDue(lastType, Date.now())) {
        lastType = Date.now()
        play('type')
      }
      const wire = s.wire[0]
      if (wire && wire.id !== prev.wire[0]?.id) {
        const effect = effectForHeadline(wire.kicker)
        if (effect) play(effect)
      }
      if (s.moment && s.moment.id !== prev.moment?.id) play(effectFor(s.moment.kind))
      const fresh = s.toasts[0]
      if (fresh && fresh.id !== prev.toasts[0]?.id && (fresh.kind === 'filled' || fresh.kind === 'partial')) play('tick')
    })
  }, [])
}
