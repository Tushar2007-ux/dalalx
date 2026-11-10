import { useEffect } from 'react'
import { effectFor, play, unlockSoundOnFirstGesture } from '../lib/sound'
import { useMarket } from '../store/market'

/** Plays the effects for big moments and for your own fills, if sound is on. */
export function useSounds(): void {
  useEffect(() => {
    unlockSoundOnFirstGesture()
    return useMarket.subscribe((s, prev) => {
      if (!s.sound) return
      if (s.moment && s.moment.id !== prev.moment?.id) play(effectFor(s.moment.kind))
      const fresh = s.toasts[0]
      if (fresh && fresh.id !== prev.toasts[0]?.id && (fresh.kind === 'filled' || fresh.kind === 'partial')) play('tick')
    })
  }, [])
}
