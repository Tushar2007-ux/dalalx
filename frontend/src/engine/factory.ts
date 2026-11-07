import type { CommandSource, WorkerLoad } from '../lib/types'

/**
 * Pure helpers for the Engine Room's animation, kept apart from React so they
 * are easy to test.
 *
 * The real workers handle anything from five to tens of thousands of commands
 * a second. Drawing one paper ticket per command would melt the browser, so
 * each lane draws at most {@link MAX_TICKETS_PER_SECOND} tickets a second and,
 * when it's busier than that, each ticket stands for several commands (the
 * lane says how many).
 */
export const MAX_TICKETS_PER_SECOND = 36

/** Ticket colours (CSS variables from the design tokens) and labels per source. */
export const SOURCE_STYLE: Record<CommandSource, { label: string; colour: string; who: string }> = {
  PRESENTER: { label: 'You', colour: 'var(--ink)', who: 'the presenter at this screen (and Rush Hour)' },
  AUDIENCE: { label: 'Audience', colour: 'var(--pencil)', who: 'phones in the room' },
  BOT: { label: 'Bots', colour: 'var(--buy)', who: 'the bot thread' },
  CLOCK: { label: 'Clock', colour: 'var(--ink-muted)', who: 'the exchange clock, 5 ticks a second' },
  SYSTEM: { label: 'System', colour: 'var(--accent)', who: 'book snapshots and surveillance' },
}

/** @return how many real commands one drawn ticket stands for (1 when the lane is quiet) */
export function ticketScale(commandsPerSecond: number, cap = MAX_TICKETS_PER_SECOND): number {
  if (commandsPerSecond <= cap) return 1
  return Math.ceil(commandsPerSecond / cap)
}

/**
 * Decides which tickets to print in the next slice of time.
 *
 * Fractions carry over between calls in `carry`, so a source at 2.5 a second
 * really prints 5 tickets every 2 seconds rather than rounding to 2 or 3.
 *
 * @param rates   commands per second, per source
 * @param seconds the slice of time (e.g. 0.1)
 * @param carry   fractional tickets left over from earlier slices (updated in place)
 * @param scale   commands per ticket for this lane
 * @return the sources of the tickets to print now
 */
export function ticketsFor(rates: Partial<Record<CommandSource, number>>, seconds: number,
  carry: Partial<Record<CommandSource, number>>, scale: number): CommandSource[] {
  const out: CommandSource[] = []
  for (const [source, rate] of Object.entries(rates) as [CommandSource, number][]) {
    const due = (carry[source] ?? 0) + (rate * seconds) / scale
    const whole = Math.floor(due)
    carry[source] = due - whole
    for (let i = 0; i < whole; i++) out.push(source)
  }
  return out
}

/** Total commands per second arriving at a worker, from every source. */
export function arrivalRate(w: WorkerLoad): number {
  return Object.values(w.arrivalsPerSecond).reduce((a, b) => a + (b ?? 0), 0)
}

/**
 * The height (0–1) of a queue bin. Queues range from 0 to 100,000, so the
 * scale is logarithmic: 10 waiting is already visible, 100,000 is full.
 */
export function queueFill(pending: number, capacity: number): number {
  if (pending <= 0) return 0
  return Math.min(1, Math.log10(pending + 1) / Math.log10(capacity + 1))
}

/** Plain-English state of a worker thread, for the press's label. */
export function threadMood(w: Pick<WorkerLoad, 'state' | 'running' | 'pending' | 'perSecond'>): string {
  if (w.running || w.pending > 0) return 'Pressing'
  if (w.state === 'WAITING' || w.state === 'TIMED_WAITING') return 'Asleep in take()'
  if (w.state === 'TERMINATED') return 'Stopped'
  return w.perSecond > 0 ? 'Pressing' : 'Idle'
}

/** Compact counts: 950 → "950", 12,400 → "12.4k", 2,300,000 → "2.3M". */
export function compact(n: number): string {
  if (n < 1000) return Math.round(n).toString()
  if (n < 1_000_000) return `${(n / 1000).toFixed(n < 10_000 ? 1 : 0)}k`
  return `${(n / 1_000_000).toFixed(1)}M`
}
