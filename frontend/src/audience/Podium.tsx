import { Monogram, Portrait } from '../design'
import { botKey } from '../lib/market'
import { monogramOf } from '../lib/monogram'
import type { Standing } from '../lib/types'
import { closingVerdict, signedRupees } from './guest'

type BotKey = 'munim' | 'momentum' | 'panic' | 'whale' | 'spoofer' | 'inspector'

/** Gold, silver, bronze — in the paper's words. */
const PLACES = ['First', 'Second', 'Third']

/**
 * The closing bell: a front page announcing who won the session.
 *
 * A round of audience trading needs an ending, or the leaderboard is just a
 * list that keeps moving. The presenter calls it, the page prints the result,
 * and everyone in the room finds out where they came.
 */
export function Podium({ standings, guests, onClose }:
  { standings: Standing[]; guests: number; onClose: () => void }) {
  const top = standings.slice(0, 3)
  const verdict = closingVerdict(standings)
  if (!verdict) return null
  const winner = verdict.winner
  const audience = standings.filter((s) => s.kind === 'AUDIENCE')

  return (
    <div className="dx-podium" role="dialog" aria-label="Closing bell" onClick={onClose}>
      <article className="dx-podium-sheet" onClick={(e) => e.stopPropagation()}>
        <header className="dx-podium-mast">
          <span>Closing bell</span>
          <strong>The DalalX Times</strong>
          <span>{guests} in the audience</span>
        </header>
        <div className="dx-podium-kicker">Final edition</div>
        <h2 className="dx-podium-headline">
          {winner.name} {winner.kind === 'BOT' ? 'holds the floor' : 'wins the floor'}
        </h2>
        <p className="dx-podium-deck">{verdict.deck}</p>

        <ol className="dx-podium-steps">
          {[1, 0, 2].map((i) => top[i] && (
            <li key={top[i].traderId} className={`dx-podium-step is-${i + 1}`}>
              <span className="dx-podium-face"><Face standing={top[i]} /></span>
              <span className="dx-podium-place">{PLACES[i]}</span>
              <b className="dx-podium-name">{top[i].name}</b>
              <span className={`dx-podium-pnl dx-num${top[i].pnl >= 0 ? ' is-up' : ' is-down'}`}>{signedRupees(top[i].pnl)}</span>
              <span className="dx-podium-block" aria-hidden="true"><i>{i + 1}</i></span>
            </li>
          ))}
        </ol>

        <footer className="dx-podium-foot">
          <span>{standings.length} traders · {audience.length} from the audience · paper money only</span>
          <button type="button" className="dx-focus" onClick={onClose}>Back to the floor</button>
        </footer>
      </article>
    </div>
  )
}

function Face({ standing }: { standing: Standing }) {
  const key = botKey(standing.traderId)
  if (key && key !== 'you') return <Portrait bot={key as BotKey} size={56} crop="head" />
  return <Monogram bot={standing.kind === 'PRESENTER' ? 'YO' : monogramOf(standing.name)} size={52} />
}
