import { signedRupees } from '../audience/guest'
import { Odometer } from '../terminal/Odometer'
import { useMarket } from '../store/market'

/**
 * Your running profit or loss, in the section bar of every page, rolling like
 * a mechanical counter as it changes. It reads the same leaderboard figure
 * the Audience page ranks you by, so the two can never disagree.
 */
export function MastheadPnl() {
  const account = useMarket((s) => s.account)
  const me = useMarket((s) => s.board?.standings.find((x) => x.traderId === 'YOU'))
  if (account) {
    const tone = account.profit > 0 ? 'up' : account.profit < 0 ? 'down' : undefined
    return (
      <a href="#/account" className={`dx-pnl dx-focus${tone ? ` is-${tone}` : ''}`} title={`${account.name}'s profit since opening`}>
        <span className="dx-pnl-label">Your P&amp;L</span>
        <Odometer value={signedRupees(account.profit)} tone={tone} />
      </a>
    )
  }
  if (!me) return null
  const tone = me.pnl > 0 ? 'up' : me.pnl < 0 ? 'down' : undefined
  return (
    <a href="#/floor" className={`dx-pnl dx-focus${tone ? ` is-${tone}` : ''}`} title={`Your profit and loss · rank #${me.rank}`}>
      <span className="dx-pnl-label">Your P&amp;L</span>
      <Odometer value={signedRupees(me.pnl)} tone={tone} />
    </a>
  )
}
