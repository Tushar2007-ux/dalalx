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
