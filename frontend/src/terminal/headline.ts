import { changePct, formatPaise } from '../lib/format'
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
