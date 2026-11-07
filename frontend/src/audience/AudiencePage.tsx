import QRCode from 'qrcode'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Monogram, Portrait, SectionLabel } from '../design'
import { api } from '../lib/api'
import { clockTime, formatPaise, formatQty } from '../lib/format'
import { botKey } from '../lib/market'
import { monogramOf } from '../lib/monogram'
import type { Standing, Trade } from '../lib/types'
import { Shell } from '../shell/Shell'
import { useMarket } from '../store/market'
import { joinUrl, rankMoves, signedRupees } from './guest'
import './audience.css'
import { VivaTag } from '../viva/VivaTag'

type BotKey = 'munim' | 'momentum' | 'panic' | 'whale' | 'spoofer' | 'inspector'

/**
 * The big screen for audience trading: a huge QR code that opens the phone
 * page, the live leaderboard (presenter, guests and bots, ranked by profit),
 * and a feed of the trades people in the room are making.
 */
export function AudiencePage() {
  const board = useMarket((s) => s.board)
  const tapes = useMarket((s) => s.tapes)
  const names = useMarket((s) => s.names)
  const connection = useMarket((s) => s.connection)
  const [addresses, setAddresses] = useState<string[]>([])
  const [pick, setPick] = useState(0)
  const [svg, setSvg] = useState('')
  const previous = useRef<Standing[]>([])
  const [moves, setMoves] = useState<Record<string, number>>({})

  useEffect(() => {
    api.invite().then((i) => setAddresses(i.addresses)).catch(() => {})
  }, [connection])

  const url = joinUrl(location, addresses[pick] ?? null)
  useEffect(() => {
    if (!url) { setSvg(''); return }
    QRCode.toString(url, { type: 'svg', margin: 1, errorCorrectionLevel: 'M', color: { dark: '#1A1614', light: '#FBEAE0' } })
      .then(setSvg).catch(() => setSvg(''))
  }, [url])

  useEffect(() => {
    if (!board) return
    setMoves(rankMoves(previous.current, board.standings))
    previous.current = board.standings
  }, [board])

  const crowdTrades = useMemo(() => Object.values(tapes).flat()
    .filter((t) => t.buyerId.startsWith('GUEST-') || t.sellerId.startsWith('GUEST-'))
    .sort((a, b) => b.id - a.id).slice(0, 10), [tapes])

  const standings = board?.standings ?? []
  const leader = standings[0]

  return (
    <Shell route="audience" page={7} section="Audience" headline={{
      kicker: 'Audience Edition', tone: 'neutral', state: connection === 'offline' ? 'stale' : 'live',
      headline: board?.guests ? `${board.guests} ${board.guests === 1 ? 'trader has' : 'traders have'} joined the floor from the audience`
        : 'Scan the code: you are now a trader on DalalX',
      deck: 'Same market, same bots, same matching engine. Buy and sell from your phone and climb the leaderboard.',
    }}>
      <div className="dx-aud">
        <section className="dx-aud-qr">
          <div className="dx-aud-kicker">Join the floor</div>
          <VivaTag note="audience" align="left" />
          {svg
            ? <div className="dx-aud-code" role="img" aria-label={`QR code for ${url}`} dangerouslySetInnerHTML={{ __html: svg }} />
            : <div className="dx-aud-code dx-aud-code-missing">
                <p>No Wi-Fi address found.</p>
                <p>Connect this computer and the phones to the same Wi-Fi (or this phone’s hotspot), then reload.</p>
              </div>}
          {url && <div className="dx-aud-url dx-num">{url.replace(/^https?:\/\//, '')}</div>}
          {addresses.length > 1 && (
            <div className="dx-aud-nets">
              {addresses.map((a, i) => (
                <button key={a} type="button" className={`dx-focus${i === pick ? ' is-on' : ''}`} onClick={() => setPick(i)}>{a}</button>
              ))}
            </div>
          )}
          <ol className="dx-aud-steps">
            <li>Open your camera and point it at the code</li>
            <li>Type your name and tap <b>Join</b></li>
            <li>Buy low, sell high, beat the bots</li>
          </ol>
          <div className="dx-aud-count"><span className="dx-num">{board?.guests ?? 0}</span> in the room</div>
        </section>

        <section className="dx-aud-board" aria-label="Leaderboard">
          <div className="dx-viva-row"><VivaTag note="leaderboard" /></div>
          <SectionLabel rule="double" meta={leader ? <>Leader: <b>{leader.name}</b> {signedRupees(leader.pnl)}</> : 'Updated every second'}>Leaderboard</SectionLabel>
          <ol className="dx-aud-rows">
            {standings.slice(0, 12).map((s) => (
              <li key={s.traderId} className={`dx-aud-row is-${s.kind.toLowerCase()}${s.rank === 1 ? ' is-first' : ''}`}>
                <span className="dx-aud-rank dx-num">{s.rank}</span>
                <span className="dx-aud-face"><Face standing={s} /></span>
                <span className="dx-aud-name">
                  <b>{s.name}</b>
                  <small>{s.kind === 'AUDIENCE' ? 'Audience' : s.kind === 'BOT' ? 'Correspondent' : 'Presenter'} · {formatQty(s.trades)} trades</small>
                </span>
                <Move by={moves[s.traderId] ?? 0} />
                <span className={`dx-aud-pnl dx-num${s.pnl > 0 ? ' is-up' : s.pnl < 0 ? ' is-down' : ''}`}>{signedRupees(s.pnl)}</span>
              </li>
            ))}
          </ol>
          {!standings.length && <p className="dx-aud-empty">The leaderboard prints as soon as the exchange answers.</p>}
        </section>

        <section className="dx-aud-feed" aria-label="Audience trades">
          <SectionLabel rule="bold" meta="Trades by people in this room">From the floor</SectionLabel>
          {crowdTrades.map((t) => <CrowdTrade key={t.id} trade={t} names={names} />)}
          {!crowdTrades.length && <p className="dx-aud-empty">No audience trades yet. Be the first!</p>}
        </section>
      </div>
    </Shell>
  )
}

function Face({ standing }: { standing: Standing }) {
  const key = botKey(standing.traderId)
  if (key && key !== 'you') return <Portrait bot={key as BotKey} size={40} crop="head" />
  return <Monogram bot={standing.kind === 'PRESENTER' ? 'YO' : monogramOf(standing.name)} size={36} />
}

function Move({ by }: { by: number }) {
  if (!by) return <span className="dx-aud-move" />
  return <span key={by} className={`dx-aud-move ${by > 0 ? 'is-up' : 'is-down'}`}>{by > 0 ? `▲${by}` : `▼${-by}`}</span>
}

function CrowdTrade({ trade, names }: { trade: Trade; names: Record<string, string> }) {
  const guestBought = trade.buyerId.startsWith('GUEST-')
  const who = names[guestBought ? trade.buyerId : trade.sellerId] ?? 'A guest'
  const other = guestBought ? trade.sellerId : trade.buyerId
  return (
    <div className="dx-aud-trade dx-anim-slip">
      <span className="dx-num">{clockTime(trade.timestamp).slice(0, 8)}</span>
      <span><b>{who}</b> {guestBought ? 'bought' : 'sold'} {formatQty(trade.qty)} {trade.symbol} at ₹{formatPaise(trade.price)}</span>
      <span className="dx-aud-trade-vs">{guestBought ? 'from' : 'to'} {names[other] ?? other}</span>
    </div>
  )
}
