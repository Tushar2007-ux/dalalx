/**
 * The arithmetic behind a flying ticket: where a chit starts, where it lands,
 * and how far it has to travel.
 *
 * It is kept apart from the component that draws it so the path can be tested
 * without a browser — the component only turns these numbers into CSS.
 */
export interface Box { left: number; top: number; width: number; height: number }

export interface Flight {
  /** Where the chit is pinned (the centre of the level it left). */
  x: number
  y: number
  /** How far it travels, as a transform the animation ends on. */
  dx: number
  dy: number
  /** Longer journeys take a little longer, within sensible bounds. */
  ms: number
}

const MIN_MS = 320
const MAX_MS = 760
/** Pixels per second the chit travels. */
const SPEED = 1_100

const centre = (b: Box) => ({ x: b.left + b.width / 2, y: b.top + b.height / 2 })

/**
 * A ticket's journey from the order-book level that was eaten to the top of
 * the trade tape, where the trade is about to be printed.
 */
export function flightPath(from: Box, to: Box): Flight {
  const a = centre(from)
  const b = centre(to)
  const dx = b.x - a.x
  const dy = b.y - a.y
  const distance = Math.hypot(dx, dy)
  const ms = Math.round(Math.min(MAX_MS, Math.max(MIN_MS, (distance / SPEED) * 1000)))
  return { x: a.x, y: a.y, dx, dy, ms }
}

/** A chit in the air, with the text it carries. */
export interface Chit extends Flight { id: number; label: string; side: 'bid' | 'ask' }

type Listener = (chit: Chit) => void
const listeners = new Set<Listener>()
let nextId = 1

/** Watch for tickets being launched. Returns the unsubscribe. */
export function onFlight(listener: Listener): () => void {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

/**
 * Send a ticket from one element to another. Does nothing when either end is
 * missing or the reader has asked for less motion, so callers need no guard.
 */
export function launchTicket(from: Element | null, to: Element | null, label: string, side: 'bid' | 'ask'): void {
  if (!from || !to || !listeners.size) return
  if (typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const chit: Chit = { id: nextId++, label, side, ...flightPath(from.getBoundingClientRect(), to.getBoundingClientRect()) }
  for (const listener of listeners) listener(chit)
}
