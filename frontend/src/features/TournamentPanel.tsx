import { useEffect, useState } from 'react'
import { Button, RubberStamp, SectionLabel } from '../design'
import { api, ApiError } from '../lib/api'
import type { TournamentStandings } from '../lib/types'
import { useMarket } from '../store/market'
import { signedRupees } from '../audience/guest'
import { clockOf, secondsLeft } from './rules'

const ROUNDS = [60, 180, 300]

/**
 * The bot tournament: a timed round in which every bot on the floor starts
 * from zero, and the one that makes most money wins. The table updates live;
 * when the time is up the paper prints the winner on the wire.
 */
export function TournamentPanel() {
  const toast = useMarket((s) => s.toast)
  const [round, setRound] = useState<TournamentStandings | null>(null)
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    const load = () => api.tournament().then(setRound).catch(() => {})
    load()
    const timer = setInterval(() => { load(); setNow(Date.now()) }, 1000)
    return () => clearInterval(timer)
  }, [])

  const start = (seconds: number) => api.startTournament(seconds).then(setRound)
    .catch((e) => toast({ kind: 'rejected', title: 'Could not start the round', detail: e instanceof ApiError ? e.message : undefined }))
  const left = secondsLeft(round, now)
  const winner = round?.state === 'FINISHED' ? round.scores[0] : undefined

  return (
    <section className="dx-tournament">
      <SectionLabel rule="double" meta={round?.state === 'RUNNING' ? <span className="dx-num" style={{ color: 'var(--stamp)' }}>■ {clockOf(left)} left</span> : 'From the starting whistle'}>
        The Bot Tournament
      </SectionLabel>
      <div className="dx-tournament-body">
        <div className="dx-tournament-start">
          <p>Every bot starts at zero. The one that makes most money before the whistle wins, and the paper prints it.</p>
          <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
            {ROUNDS.map((s) => <Button key={s} size="sm" variant={round?.state === 'RUNNING' ? 'secondary' : 'primary'} onClick={() => void start(s)}>
              {round?.state === 'RUNNING' ? 'Restart' : 'Start'} a {s / 60}-minute round
            </Button>)}
            {round?.state === 'RUNNING' && <Button size="sm" variant="ghost" onClick={() => api.cancelTournament().then(() => api.tournament().then(setRound))}>Call it off</Button>}
          </div>
        </div>
        <ol className="dx-tournament-table dx-num">
          {(round?.scores ?? []).map((s) => (
            <li key={s.traderId} className={s.rank === 1 ? 'is-first' : undefined}>
              <span>{s.rank}</span><span className="dx-tournament-name">{s.name}</span>
              <span className={s.pnl > 0 ? 'is-up' : s.pnl < 0 ? 'is-down' : ''}>{signedRupees(s.pnl)}</span>
            </li>
          ))}
          {(!round || round.state === 'IDLE') && <li className="dx-tournament-empty">No round under way.</li>}
        </ol>
        {winner && <RubberStamp text="Winner" sub={winner.name} size="sm" angle={-7} animate slamKey={round?.endsAt ?? ''} backed
          style={{ position: 'absolute', right: 8, top: 4 }} />}
      </div>
    </section>
  )
}
