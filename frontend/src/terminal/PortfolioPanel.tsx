import { useEffect, useState } from 'react'
import { SectionLabel } from '../design'
import { DataTable, Empty } from '../ui'
import { api } from '../lib/api'
import { formatPaise, formatQty } from '../lib/format'
import type { Portfolio } from '../lib/types'
import { useMarket } from '../store/market'
import { signedRupees } from '../audience/guest'
import { VivaTag } from '../viva/VivaTag'

/**
 * Your portfolio: the shares you hold, what you paid on average, and your
 * profit or loss at today's prices, then where that puts you against the
 * bots and the audience. Refreshed every two seconds and after each trade.
 */
export function PortfolioPanel() {
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null)
  const myTrades = useMarket((s) => s.lastTradeBy.YOU?.at)
  const board = useMarket((s) => s.board)
  const connection = useMarket((s) => s.connection)

  useEffect(() => {
    if (connection !== 'live') return
    let stopped = false
    const load = () => api.portfolio().then((p) => !stopped && setPortfolio(p)).catch(() => {})
    load()
    const timer = setInterval(load, 2000)
    return () => { stopped = true; clearInterval(timer) }
  }, [connection, myTrades])

  const pnl = portfolio?.stats.pnl ?? 0
  const me = board?.standings.find((s) => s.traderId === 'YOU')
  const top = (board?.standings ?? []).slice(0, 5)

  return (
    <section className="dx-portfolio">
      <SectionLabel rule="bold" meta={<span className={pnl > 0 ? 'is-up' : pnl < 0 ? 'is-down' : undefined}>{signedRupees(pnl)}</span>}>Your Portfolio</SectionLabel>
      {portfolio && portfolio.holdings.length > 0
        ? <DataTable head={['Stock', 'Qty', 'Avg', 'Now', 'P&L']}>
              {portfolio.holdings.map((h) => (
                <tr key={h.symbol}>
                  <td>{h.symbol}</td>
                  <td>{formatQty(h.qty)}</td>
                  <td>{formatPaise(h.avgPrice)}</td>
                  <td>{formatPaise(h.lastPrice)}</td>
                  <td className={h.unrealised >= 0 ? 'is-up' : 'is-down'}>{signedRupees(h.unrealised)}</td>
                </tr>
              ))}
          </DataTable>
        : <Empty>No shares yet. Buy something and it will be valued here at the live price.</Empty>}

      <div className="dx-viva-row"><VivaTag note="leaderboard" /></div>
      <SectionLabel under="hair" meta={me ? <>You are <b>#{me.rank}</b> of {board?.standings.length}</> : undefined}>You vs the bots</SectionLabel>
      <ol className="dx-minilead">
        {top.map((s) => (
          <li key={s.traderId} className={s.traderId === 'YOU' ? 'is-me' : undefined}>
            <span className="dx-num">{s.rank}</span>
            <span>{s.name}{s.kind === 'AUDIENCE' && <i> · audience</i>}</span>
            <span className={`dx-num ${s.pnl > 0 ? 'is-up' : s.pnl < 0 ? 'is-down' : ''}`}>{signedRupees(s.pnl)}</span>
          </li>
        ))}
      </ol>
      <a href="#/audience" className="dx-minilead-link">Full leaderboard and audience trading →</a>
    </section>
  )
}
