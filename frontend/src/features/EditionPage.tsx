import { useEffect, useState } from 'react'
import { Button } from '../design'
import { api } from '../lib/api'
import { clockTime, formatPaise, formatQty } from '../lib/format'
import type { Case, DayReport, TournamentStandings, WireHeadline } from '../lib/types'
import { useMarket } from '../store/market'
import { dayMove, leadHeadline, moversOf } from './rules'
import './features.css'

/**
 * The Evening Edition: the day's paper, written from the day's figures and
 * laid out to print. It reads the day tape (open, high, low, last and volume
 * for every stock), the wire, the surveillance desk's cases and the
 * tournament, so everything on the page actually happened today.
 *
 * Print it (or save it as a PDF) with the button; print styles drop the
 * buttons and keep the columns.
 */
export function EditionPage() {
  const [day, setDay] = useState<DayReport | null>(null)
  const [wire, setWire] = useState<WireHeadline[]>([])
  const [cases, setCases] = useState<Case[]>([])
  const [round, setRound] = useState<TournamentStandings | null>(null)
  const edition = useMarket((s) => s.edition)

  useEffect(() => {
    api.day().then(setDay).catch(() => {})
    api.wire(50).then(setWire).catch(() => {})
    api.cases().then(setCases).catch(() => {})
    api.tournament().then(setRound).catch(() => {})
  }, [])

  const stocks = day?.stocks ?? []
  const halts = wire.filter((h) => /halt/i.test(h.text)).length
  const { gainers, losers, busiest } = moversOf(stocks)
  const news = wire.filter((h) => h.kicker === 'Simulated news').slice(0, 4)
  const caught = cases.filter((c) => c.status === 'SUSPENDED' || c.status === 'OPEN').slice(0, 3)
  const winner = round?.state === 'FINISHED' ? round.scores[0] : undefined
  const date = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })

  return (
    <div className="dx-evening" data-edition={edition}>
      <div className="dx-evening-tools">
        <a href="#/" className="dx-focus">← Back to the live paper</a>
        <Button size="sm" variant="primary" onClick={() => window.print()}>Print this edition</Button>
      </div>
      <article className="dx-evening-sheet">
        <header className="dx-evening-nameplate">
          <span>{date}</span>
          <h1>The Uncross Times</h1>
          <span>Evening Edition · Closing prices</span>
        </header>
        <h2 className="dx-evening-lead">{day ? leadHeadline(stocks, halts) : 'Setting type…'}</h2>
        <p className="dx-evening-deck">
          {day ? <>{day.trades.toLocaleString('en-IN')} trades worth ₹{formatPaise(day.turnover)} changed hands
            {busiest ? <>; {busiest.symbol} was the busiest stock with ₹{formatPaise(busiest.turnover)} traded</> : null}.</> : ' '}
        </p>
        <div className="dx-evening-cols">
          <section>
            <h3>Closing prices</h3>
            <table className="dx-evening-table dx-num">
              <thead><tr><th>Stock</th><th>Close</th><th>Day</th><th>High</th><th>Low</th><th>Volume</th></tr></thead>
              <tbody>
                {stocks.map((s) => {
                  const move = dayMove(s)
                  return (
                    <tr key={s.symbol}>
                      <td>{s.symbol}</td>
                      <td>{formatPaise(s.last)}</td>
                      <td className={move > 0 ? 'is-up' : move < 0 ? 'is-down' : ''}>{move > 0 ? '+' : ''}{move.toFixed(2)}%</td>
                      <td>{s.trades ? formatPaise(s.high) : '–'}</td>
                      <td>{s.trades ? formatPaise(s.low) : '–'}</td>
                      <td>{formatQty(s.volume)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            <h3>Movers</h3>
            <p>{gainers.length ? <>Up: {gainers.map((s) => `${s.symbol} (+${dayMove(s).toFixed(1)}%)`).join(', ')}. </> : null}
              {losers.length ? <>Down: {losers.map((s) => `${s.symbol} (${dayMove(s).toFixed(1)}%)`).join(', ')}.</> : null}
              {!gainers.length && !losers.length ? 'Nothing moved far enough to report.' : null}</p>
          </section>
          <section>
            <h3>The biggest trades</h3>
            <ol className="dx-evening-list dx-num">
              {(day?.biggest ?? []).map((t, i) => (
                <li key={i}><b>{t.symbol}</b> {formatQty(t.qty)} @ ₹{formatPaise(t.price)} <span>· ₹{formatPaise(t.price * t.qty)} · {t.buyer} bought from {t.seller} at {clockTime(Date.parse(t.at)).slice(0, 5)}</span></li>
              ))}
              {day && day.biggest.length === 0 && <li>No trades yet today.</li>}
            </ol>
            <h3>From the newsroom</h3>
            {news.length ? news.map((h) => <p key={h.id}><b>{h.text}.</b> {h.deck}</p>) : <p>A quiet day for company news.</p>}
          </section>
          <section>
            <h3>Police blotter</h3>
            {caught.length ? caught.map((c) => (
              <p key={c.id}><b>{c.traderId}</b> {c.status === 'SUSPENDED' ? 'suspended' : 'under investigation'} for {c.ruleName.toLowerCase()} in {c.symbol}: {c.summary}.</p>
            )) : <p>The Inspector reports a clean tape.</p>}
            <h3>Sport: the bot tournament</h3>
            {winner ? <p><b>{winner.name}</b> won the round, making ₹{formatPaise(Math.abs(winner.pnl))}{winner.pnl < 0 ? ' less than it started with (it lost least)' : ''}. {round!.scores.length} bots took part.</p>
              : round?.state === 'RUNNING' ? <p>A round is still being played; results in the next edition.</p>
              : <p>No round was played today. Start one from the Correspondents page.</p>}
            <h3>Circuit breakers</h3>
            <p>{halts ? `${halts} halt${halts === 1 ? '' : 's'} on the wire today.` : 'No stock was halted today.'}</p>
          </section>
        </div>
        <footer className="dx-evening-folio">Simulated exchange · paper money only · printed {clockTime(Date.now()).slice(0, 5)}</footer>
      </article>
    </div>
  )
}
