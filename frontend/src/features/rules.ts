/**
 * The arithmetic and checking behind the second batch of features: the
 * account form, the bot builder, the listing form, the tournament clock,
 * the rewind and the evening edition.
 *
 * Every function here is pure (values in, values out), so the rules can be
 * read and tested without a browser. The components that use them stay thin.
 */
import { parseRupees } from '../lib/format'
import type { BotRecipe, BotTrigger, DayStock, RewindView, Side, TournamentStandings } from '../lib/types'

/* ---- Accounts -------------------------------------------------------------- */

/** The same rules the server applies, checked before sending so the reader gets a clear message at once. */
export function checkCredentials(name: string, pin: string): string | null {
  const clean = name.trim()
  if (!/^[\p{L}0-9 ]{2,20}$/u.test(clean) || clean.replace(/[^\p{L}0-9]/gu, '').length < 2) {
    return 'A name is 2–20 letters, digits or spaces'
  }
  if (!/^\d{4,8}$/.test(pin)) return 'A PIN is 4 to 8 digits'
  return null
}

/* ---- Build your own bot ------------------------------------------------------ */

export interface RecipeForm {
  name: string
  symbol: string
  when: BotTrigger
  /** Percent for FALLS/RISES ("1.5"), seconds for EVERY ("10"). */
  amountText: string
  side: Side
  qtyText: string
  maxText: string
}

export type RecipeCheck = { recipe: BotRecipe; error?: undefined } | { recipe?: undefined; error: string }

const whole = (text: string) => (/^\d+$/.test(text.trim()) ? Number(text.trim()) : NaN)

/** Turns the form into a recipe the server will accept, or one clear error. */
export function checkRecipe(f: RecipeForm): RecipeCheck {
  const name = f.name.trim()
  if (!name || name.length > 40 || !/[A-Za-z0-9]/.test(name)) return { error: 'Give the bot a name with letters or digits' }
  let amount: number
  if (f.when === 'EVERY') {
    amount = whole(f.amountText)
    if (!(amount >= 1 && amount <= 300)) return { error: 'Repeat every 1–300 seconds' }
  } else {
    const pct = Number(f.amountText.trim())
    amount = Math.round(pct * 100)
    if (!Number.isFinite(pct) || amount < 10 || amount > 2000) return { error: 'A move of 0.1%–20%' }
  }
  const qty = whole(f.qtyText)
  if (!(qty >= 1 && qty <= 5000)) return { error: 'Trade 1–5,000 shares at a time' }
  const maxShares = whole(f.maxText)
  if (!(maxShares >= qty && maxShares <= 50_000)) return { error: 'The holding limit must be at least the trade size, and at most 50,000' }
  return { recipe: { name, symbol: f.symbol, when: f.when, amount, side: f.side, qty, maxShares } }
}

/** The rule in one sentence, as the bot's byline will print it. */
export function describeRecipe(r: BotRecipe): string {
  const trigger = r.when === 'EVERY'
    ? `every ${r.amount} ${r.amount === 1 ? 'second' : 'seconds'}`
    : `whenever the price ${r.when === 'FALLS' ? 'falls' : 'rises'} ${(r.amount / 100).toFixed(2)}%`
  return `${r.side === 'BUY' ? 'Buys' : 'Sells'} ${r.qty} ${r.symbol} ${trigger}, holding at most ${r.maxShares}.`
}

/* ---- Listing a new stock ------------------------------------------------------ */

export type ListingCheck = { symbol: string; name: string; price: number; error?: undefined } | { error: string }

export function checkListing(symbolText: string, nameText: string, priceText: string, listed: string[]): ListingCheck {
  const symbol = symbolText.trim().toUpperCase()
  if (!/^[A-Z][A-Z0-9]{1,11}$/.test(symbol)) return { error: 'A symbol is 2–12 capital letters or digits, starting with a letter' }
  if (listed.includes(symbol)) return { error: `${symbol} is already listed` }
  if (listed.length >= 12) return { error: 'The exchange is full: at most 12 stocks' }
  const name = nameText.trim() || symbol
  if (name.length > 60) return { error: 'A company name is at most 60 characters' }
  const price = parseRupees(priceText)
  if (price === null || price < 100) return { error: 'Enter an opening price like 250.00' }
  if (price % 5 !== 0) return { error: 'Prices move in ticks of ₹0.05' }
  return { symbol, name, price }
}

/* ---- Tournament -------------------------------------------------------------- */

/** Seconds left in the round (0 when it is not running). */
export function secondsLeft(t: TournamentStandings | null, now: number): number {
  if (!t || t.state !== 'RUNNING' || !t.endsAt) return 0
  return Math.max(0, Math.ceil((Date.parse(t.endsAt) - now) / 1000))
}

export function clockOf(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/* ---- Rewind --------------------------------------------------------------------- */

export const REWIND_SPEEDS = [1, 10, 60, 300, 600]

/** How far through the tape, 0–100. */
export function progressOf(v: RewindView | null): number {
  if (!v || v.total === 0) return 0
  return Math.min(100, Math.round((v.done / v.total) * 100))
}

/** A stock's move since its first trade in the rewind, in percent. */
export function rewindMove(open: number, last: number): number {
  return open ? ((last - open) / open) * 100 : 0
}

/** "session-20261117-091500.log" → "17 Nov 2026, 09:15 UTC" (falls back to the name). */
export function sessionLabel(name: string): string {
  const m = /^session-(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})\d{2}\.log$/.exec(name)
  if (!m) return name
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  return `${Number(m[3])} ${months[Number(m[2]) - 1]} ${m[1]}, ${m[4]}:${m[5]} UTC`
}

/* ---- The evening edition ------------------------------------------------------------ */

/** The day's change against the reference (yesterday's close), in percent; 0 for a stock that never traded. */
export function dayMove(s: DayStock): number {
  return s.trades && s.reference ? ((s.last - s.reference) / s.reference) * 100 : 0
}

export interface Movers {
  gainers: DayStock[]
  losers: DayStock[]
  busiest: DayStock | null
}

/** Who rose, who fell and which stock traded most, for the evening paper's market report. */
export function moversOf(stocks: DayStock[]): Movers {
  const traded = stocks.filter((s) => s.trades > 0)
  const byMove = [...traded].sort((a, b) => dayMove(b) - dayMove(a))
  return {
    gainers: byMove.filter((s) => dayMove(s) > 0).slice(0, 3),
    losers: byMove.filter((s) => dayMove(s) < 0).reverse().slice(0, 3),
    busiest: traded.length ? [...traded].sort((a, b) => b.turnover - a.turnover)[0] : null,
  }
}

/** The evening paper's lead headline, written from the day's figures. */
export function leadHeadline(stocks: DayStock[], halts: number): string {
  const { gainers, losers } = moversOf(stocks)
  const top = gainers[0]
  const bottom = losers[0]
  if (halts > 0 && bottom) return `Breakers trip as ${bottom.symbol} slides ${Math.abs(dayMove(bottom)).toFixed(1)}%`
  if (top && (!bottom || dayMove(top) >= Math.abs(dayMove(bottom)))) return `${top.symbol} leads the market, up ${dayMove(top).toFixed(1)}%`
  if (bottom) return `${bottom.symbol} drags, down ${Math.abs(dayMove(bottom)).toFixed(1)}%`
  return 'A quiet day on the floor'
}
