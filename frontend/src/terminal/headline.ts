import { changePct, clockTime, formatPaise } from '../lib/format'
import type { WireHeadline } from '../lib/types'
import type { Connection } from '../store/market'

/**
 * The banner headline for the selected stock. Step 9's HeadlineWriter (server
 * side) will replace this with real news; until then the paper writes a plain
 * price story so the banner is never empty.
 */
export interface Headline {
  kicker: string
  headline: string
  deck?: string
  tone: 'neutral' | 'up' | 'down'
  state: 'live' | 'breaking' | 'stale'
}

export function priceHeadline(symbol: string, price: number | undefined, reference: number | undefined,
                              connection: Connection): Headline {
  if (connection === 'offline') {
    return {
      kicker: 'Market Closed', tone: 'neutral', state: 'stale',
      headline: 'The presses have stopped: the exchange is not answering',
      deck: 'Prices below are the last ones printed. The terminal reconnects by itself when the exchange is back.',
    }
  }
  if (price === undefined || reference === undefined) {
    return { kicker: 'Latest', tone: 'neutral', state: 'live', headline: `${symbol} awaits its first trade of the session` }
  }
  const pct = changePct(price, reference)
  if (Math.abs(pct) < 0.005) {
    return { kicker: 'Latest', tone: 'neutral', state: 'live', headline: `${symbol} unchanged at ₹${formatPaise(price)}` }
  }
  const up = pct > 0
  return {
    kicker: 'Latest',
    tone: up ? 'up' : 'down',
    state: 'live',
    headline: `${symbol} ${up ? 'rises' : 'falls'} ${Math.abs(pct).toFixed(2)}% to ₹${formatPaise(price)}`,
    deck: `Against a reference price of ₹${formatPaise(reference)}.`,
  }
}

/** A wire story stays in the banner for this long before the price story returns. */
export const BANNER_FRESH_MS = 90_000

const TONES = { UP: 'up', DOWN: 'down', ALERT: 'down', NEUTRAL: 'neutral' } as const

/**
 * The banner prefers real news: the newest wire story (about this stock or the
 * whole market) if it is fresh, otherwise the plain price story.
 */
export function bannerHeadline(wire: WireHeadline[], symbol: string, price: number | undefined,
                               reference: number | undefined, connection: Connection, now: number): Headline & { time?: string } {
  if (connection !== 'offline') {
    const news = wire.find((h) => h.symbol === null || h.symbol === symbol)
    if (news && now - Date.parse(news.time) < BANNER_FRESH_MS) {
      return {
        kicker: news.kicker,
        headline: news.text,
        deck: news.deck ?? undefined,
        tone: TONES[news.tone],
        state: news.tone === 'ALERT' ? 'breaking' : 'live',
        time: clockTime(news.time).slice(0, 8),
      }
    }
  }
  return priceHeadline(symbol, price, reference, connection)
}
