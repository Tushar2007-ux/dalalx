/**
 * How much graphics work this machine should be asked to do.
 *
 * The 3D pieces of the paper are the one part that can make a demo stutter on
 * a projector laptop, so the decision is made once, here, from what the
 * browser will tell us about the machine — and it can always be overruled by
 * hand from the Tools menu.
 *
 * The rule itself is a plain function of plain numbers so it can be tested
 * without a browser: nothing in here touches the DOM.
 */
export type Tier = 'off' | 'low' | 'high'

export interface Machine {
  /** Does this browser have WebGL at all? */
  webgl: boolean
  /** Has the reader asked for less motion? Then none of this runs. */
  reducedMotion: boolean
  /** Rough gigabytes of RAM, where the browser reports it. */
  memoryGb?: number
  /** Logical CPU cores. */
  cores?: number
  /** Screen density. A phone drawing four pixels for every one is expensive. */
  pixelRatio: number
  /** Narrow screens get the cheap version whatever else is true. */
  width: number
}

/**
 * The tier for a machine. `off` means draw nothing and leave the flat version
 * in place — which is always a complete, usable page, never a blank hole.
 */
export function tierFor(m: Machine): Tier {
  if (!m.webgl || m.reducedMotion) return 'off'
  if (m.width < 700) return 'low'
  if ((m.cores ?? 4) <= 2) return 'low'
  if ((m.memoryGb ?? 4) <= 2) return 'low'
  return 'high'
}

/** How many device pixels to draw per CSS pixel, which is the main cost knob. */
export function pixelRatioFor(tier: Tier, devicePixelRatio: number): number {
  if (tier === 'off') return 0
  return Math.min(devicePixelRatio, tier === 'high' ? 2 : 1.25)
}

/** How many paper slips a press may have in the air at once. */
export function slipBudget(tier: Tier): number {
  return tier === 'high' ? 90 : tier === 'low' ? 24 : 0
}

/** Reads the real machine. The only part of this file that needs a browser. */
export function readMachine(): Machine {
  let webgl = false
  try {
    const canvas = document.createElement('canvas')
    webgl = !!(canvas.getContext('webgl2') ?? canvas.getContext('webgl'))
  } catch { webgl = false }
  const nav = navigator as Navigator & { deviceMemory?: number }
  return {
    webgl,
    reducedMotion: typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches,
    memoryGb: nav.deviceMemory,
    cores: navigator.hardwareConcurrency,
    pixelRatio: devicePixelRatio,
    width: innerWidth,
  }
}
