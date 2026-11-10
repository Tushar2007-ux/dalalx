/**
 * The paper's sound effects, synthesised with the Web Audio API (no audio
 * files): a bell when trading resumes, a heavy thud when the circuit breaker
 * slams shut, a rubber stamp when a trader is caught, and a soft "tick" when
 * one of your orders fills.
 *
 * Browsers only allow sound after the person has clicked or pressed a key,
 * so the audio context is created lazily and resumed on the first gesture.
 */
export type Effect = 'bell' | 'thud' | 'stamp' | 'tick'

/** Which effect goes with which big moment. Pure, for tests. */
export function effectFor(moment: 'extra' | 'bell' | 'caught'): Effect {
  return moment === 'bell' ? 'bell' : moment === 'extra' ? 'thud' : 'stamp'
}

let ctx: AudioContext | null = null

function audio(): AudioContext | null {
  if (typeof window === 'undefined') return null
  const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
  if (!Ctor) return null
  if (!ctx) {
    try { ctx = new Ctor() } catch { return null }
  }
  if (ctx.state === 'suspended') void ctx.resume().catch(() => {})
  return ctx
}

/** Call once at start-up: the first click or key press unlocks sound. */
export function unlockSoundOnFirstGesture(): void {
  if (typeof window === 'undefined') return
  const unlock = () => { audio(); window.removeEventListener('pointerdown', unlock); window.removeEventListener('keydown', unlock) }
  window.addEventListener('pointerdown', unlock)
  window.addEventListener('keydown', unlock)
}

function tone(c: AudioContext, freq: number, start: number, length: number, gain: number, type: OscillatorType = 'sine') {
  const osc = c.createOscillator()
  const g = c.createGain()
  osc.type = type
  osc.frequency.setValueAtTime(freq, start)
  g.gain.setValueAtTime(0.0001, start)
  g.gain.exponentialRampToValueAtTime(gain, start + 0.01)
  g.gain.exponentialRampToValueAtTime(0.0001, start + length)
  osc.connect(g).connect(c.destination)
  osc.start(start)
  osc.stop(start + length + 0.05)
}

function noise(c: AudioContext, start: number, length: number, gain: number, lowpass: number) {
  const buffer = c.createBuffer(1, Math.floor(c.sampleRate * length), c.sampleRate)
  const data = buffer.getChannelData(0)
  for (let i = 0; i < data.length; i++) data[i] = (Math.random() * 2 - 1) * (1 - i / data.length) ** 3
  const src = c.createBufferSource()
  src.buffer = buffer
  const filter = c.createBiquadFilter()
  filter.type = 'lowpass'
  filter.frequency.value = lowpass
  const g = c.createGain()
  g.gain.value = gain
  src.connect(filter).connect(g).connect(c.destination)
  src.start(start)
}

export function play(effect: Effect): void {
  const c = audio()
  if (!c || c.state !== 'running') return
  const t = c.currentTime + 0.02
  switch (effect) {
    case 'bell':
      // A struck bell: a fundamental with inharmonic partials, rung twice.
      for (const at of [t, t + 0.55]) {
        tone(c, 880, at, 2.2, 0.22)
        tone(c, 880 * 2.76, at, 1.2, 0.08)
        tone(c, 880 * 5.4, at, 0.6, 0.04)
        tone(c, 440, at, 2.6, 0.08)
      }
      break
    case 'thud':
      // The press slamming shut: a low drop in pitch plus a burst of low noise.
      tone(c, 110, t, 0.6, 0.6, 'triangle')
      tone(c, 55, t, 0.9, 0.45)
      noise(c, t, 0.35, 0.5, 600)
      break
    case 'stamp':
      // A rubber stamp: a short, damped knock.
      noise(c, t, 0.12, 0.6, 1800)
      tone(c, 180, t, 0.18, 0.35, 'square')
      break
    case 'tick':
      tone(c, 1320, t, 0.09, 0.08)
      tone(c, 1760, t + 0.06, 0.12, 0.06)
      break
  }
}
