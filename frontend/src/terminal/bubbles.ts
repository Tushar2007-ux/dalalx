import { formatQty } from '../lib/format'
import type { LastTrade } from '../store/market'

/**
 * What each bot says in its speech bubble when it trades: a line in its own
 * voice, built from what it just did. Pure, so it can be tested.
 */
export function bubbleLine(botId: string, last: LastTrade): string {
  const qty = formatQty(last.qty)
  const sym = last.symbol
  const bought = last.side === 'BUY'
  switch (botId) {
    case 'MUNIM': return bought ? `Bid filled: ${qty} ${sym}. Quoting again.` : `Offer lifted: ${qty} ${sym}. Re-quoting.`
    case 'MOMENTUM': return bought ? `It's going up! +${qty} ${sym}` : `Trend's turning. Out of ${qty} ${sym}`
    case 'PANIC': return bought ? `Phew… buying back ${qty} ${sym}` : `Sell! SELL! ${qty} ${sym}!`
    case 'WHALE': return bought ? `Accumulating. ${qty} ${sym}, quietly.` : `Dumping ${qty} ${sym}. Slice by slice.`
    case 'SPOOFER': return bought ? `Nothing to see here… ${qty} ${sym}` : `Those big bids? Not mine. Sold ${qty} ${sym}`
    default: return `${bought ? 'Bought' : 'Sold'} ${qty} ${sym}`
  }
}
