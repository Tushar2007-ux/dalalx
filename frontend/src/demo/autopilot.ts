import { useEffect } from 'react'
import { create } from 'zustand'
import { go } from '../shell/route'
import { useMarket } from '../store/market'
import { DEMO, type DemoStep } from './script'

/**
 * The autopilot's own small store: which step is showing, how long it has
 * been showing (paused time doesn't count), and whether it is running.
 */
export interface DemoState {
  running: boolean
  paused: boolean
  step: number
  elapsed: number
  start: () => void
  stop: () => void
  togglePause: () => void
  next: () => void
  tick: (seconds: number) => void
}

export const useDemo = create<DemoState>()((set, get) => ({
  running: false,
  paused: false,
  step: 0,
  elapsed: 0,
  start: () => set({ running: true, paused: false, step: 0, elapsed: 0 }),
  stop: () => set({ running: false, paused: false }),
  togglePause: () => set((s) => ({ paused: !s.paused })),
  next: () => {
    const step = get().step + 1
    if (step >= DEMO.length) set({ running: false, step: 0, elapsed: 0 })
    else set({ step, elapsed: 0 })
  },
  tick: (seconds) => set((s) => ({ elapsed: s.elapsed + seconds })),
}))

/** Should the tour move on from `step`? Pure, so it can be unit-tested. */
export function stepDone(step: DemoStep, elapsed: number, market: Parameters<NonNullable<DemoStep['until']>>[0]): boolean {
  if (elapsed < step.seconds) return false
  if (!step.until) return true
  return step.until(market) || elapsed >= (step.maxSeconds ?? step.seconds)
}

const TICK_MS = 250

/**
 * Drives the tour: when a step begins it shows the step's page and runs its
 * action once; every quarter-second it checks whether to move on.
 */
export function useAutopilot(): void {
  const running = useDemo((s) => s.running)
  const step = useDemo((s) => s.step)

  // A new step: show its page and run its action.
  useEffect(() => {
    if (!running) return
    const current = DEMO[step]
    go(current.route)
    Promise.resolve(current.action?.(useMarket.getState())).catch(() => {
      // An action failing (say, no open case to suspend) shouldn't stop the tour.
    })
  }, [running, step])

  // The clock.
  useEffect(() => {
    if (!running) return
    const timer = setInterval(() => {
      const demo = useDemo.getState()
      if (demo.paused) return
      demo.tick(TICK_MS / 1000)
      if (stepDone(DEMO[demo.step], demo.elapsed + TICK_MS / 1000, useMarket.getState())) demo.next()
    }, TICK_MS)
    return () => clearInterval(timer)
  }, [running])
}
