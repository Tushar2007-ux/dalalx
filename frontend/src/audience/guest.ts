import type { Joined, Standing } from '../lib/types'

/**
 * Small, pure helpers for audience trading (the QR code page and the phone page).
 */

const KEY = 'dalalx-guest'

/** The phone remembers who it is, so a refresh doesn't make you join again. Fails quietly in private mode. */
export function loadGuest(): Joined | null {
  try {
    const raw = localStorage.getItem(KEY)
    const g = raw ? (JSON.parse(raw) as Joined) : null
    return g && typeof g.token === 'string' && typeof g.name === 'string' ? g : null
  } catch {
    return null
  }
}

export function saveGuest(g: Joined | null): void {
  try {
    if (g) localStorage.setItem(KEY, JSON.stringify(g))
    else localStorage.removeItem(KEY)
  } catch { /* not remembered; they can join again */ }
}

/**
 * The address phones should open. The presenter usually browses on
 * "localhost", which a phone can't reach, so the page swaps in the
 * computer's Wi-Fi address and keeps the port.
 *
 * @param page     the presenter's current location
 * @param lanIp    the computer's address on the local network, if known
 */
export function joinUrl(page: { protocol: string; hostname: string; port: string }, lanIp: string | null): string | null {
  const local = page.hostname === 'localhost' || page.hostname === '127.0.0.1' || page.hostname === '::1'
  const host = local ? lanIp : page.hostname
  if (!host) return null
  const port = page.port ? `:${page.port}` : ''
  return `${page.protocol}//${host}${port}/#/join`
}

/**
 * Rank changes since the last leaderboard: positive = climbed. A trader new to
 * the board gets 0.
 */
export function rankMoves(before: Standing[], after: Standing[]): Record<string, number> {
  const was = new Map(before.map((s) => [s.traderId, s.rank]))
  return Object.fromEntries(after.map((s) => [s.traderId, was.has(s.traderId) ? was.get(s.traderId)! - s.rank : 0]))
}

/** Signed rupees for P&L, e.g. +₹1,250 or −₹80. Paise in, whole rupees out. */
export function signedRupees(paise: number): string {
  const rupees = Math.round(paise / 100)
  const sign = rupees > 0 ? '+' : rupees < 0 ? '−' : ''
  return `${sign}₹${Math.abs(rupees).toLocaleString('en-IN')}`
}

/**
 * What the closing-bell front page says about a session.
 *
 * The headline and the deck are the only judgement the paper makes, so the
 * rule behind them lives here, in one plain function a test can read: did
 * anyone from the audience finish ahead of every bot?
 */
export function closingVerdict(standings: Standing[]): { winner: Standing; humansBeatBots: boolean; deck: string } | null {
  const winner = standings[0]
  if (!winner) return null
  const bestGuest = standings.find((s) => s.kind === 'AUDIENCE')
  const bestBot = standings.find((s) => s.kind === 'BOT')
  const humansBeatBots = bestGuest !== undefined && (bestBot === undefined || bestGuest.rank < bestBot.rank)
  const deck = humansBeatBots
    ? `The audience beat the machines: ${bestGuest!.name} finished ahead of every correspondent on the floor.`
    : winner.kind === 'BOT'
      ? 'The correspondents kept their crown this session. The audience will want another go.'
      : 'Closed at the bell, with profit and loss marked to the last traded price.'
  return { winner, humansBeatBots, deck }
}
