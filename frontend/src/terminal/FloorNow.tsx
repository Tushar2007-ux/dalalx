import { useEffect, useState } from 'react'
import { Monogram, Portrait } from '../design'
import { formatQty } from '../lib/format'
import { botKey } from '../lib/market'
import { monogramOf } from '../lib/monogram'
import { useMarket } from '../store/market'
import { bubbleLine } from './bubbles'

type BotKey = 'munim' | 'momentum' | 'panic' | 'whale' | 'spoofer' | 'inspector'

const TRADERS = ['MUNIM', 'MOMENTUM', 'PANIC', 'WHALE', 'SPOOFER']
const NAMES: Record<string, string> = { MUNIM: 'The Munim', MOMENTUM: 'Momentum', PANIC: 'Panic', WHALE: 'The Whale', SPOOFER: 'The Spoofer' }
/** A head stays "lit" this long after its bot trades. */
export const ACTIVE_MS = 1800

/**
 * "On the floor now": the bots' heads beside the stock tabs. A head lifts and
 * gets a ring the moment its bot trades, with what it just did underneath;
 * a suspended trader is greyed out with a stamp. It re-renders a few times a
 * second, so the heads settle back after their moment.
 */
export function FloorNow() {
  const lastTradeBy = useMarket((s) => s.lastTradeBy)
  const cases = useMarket((s) => s.cases)
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 400)
    return () => clearInterval(timer)
  }, [])

  // Only the bot that traded most recently speaks, so bubbles never pile on top of each other.
  const speaker = TRADERS.filter((id) => lastTradeBy[id] && now - lastTradeBy[id].at < ACTIVE_MS)
    .sort((a, b) => lastTradeBy[b].at - lastTradeBy[a].at)[0]

  return (
    <div className="dx-floornow" aria-label="Bots trading now">
      <span className="dx-floornow-label">On the floor now</span>
      {TRADERS.map((id) => {
        const last = lastTradeBy[id]
        const active = !!last && now - last.at < ACTIVE_MS
        const suspended = cases.some((c) => c.traderId === id && c.status === 'SUSPENDED')
        const key = botKey(id)
        return (
          <span key={id} className={`dx-floornow-bot${active ? ' is-active' : ''}${suspended ? ' is-suspended' : ''}`}
            title={suspended ? `${NAMES[id]} · suspended` : last ? `${NAMES[id]} · last ${last.side === 'BUY' ? 'bought' : 'sold'} ${formatQty(last.qty)} ${last.symbol}` : NAMES[id]}>
            {active && !suspended && id === speaker && (
              <span key={last!.at} className={`dx-bubble ${last!.side === 'BUY' ? 'is-buy' : 'is-sell'}`} role="status">{bubbleLine(id, last!)}</span>
            )}
            <span className="dx-floornow-head" style={{ borderColor: active ? (last!.side === 'BUY' ? 'var(--buy)' : 'var(--sell)') : undefined }}>
              {key && key !== 'you' ? <Portrait bot={key as BotKey} size={38} crop="head" /> : <Monogram bot={monogramOf(id)} size={34} />}
            </span>
            <span className="dx-floornow-what dx-num">
              {suspended ? 'Suspended' : active ? `${last!.side === 'BUY' ? '▲' : '▼'} ${formatQty(last!.qty)} ${last!.symbol.slice(0, 4)}` : NAMES[id]}
            </span>
          </span>
        )
      })}
    </div>
  )
}
