import { useEffect, useMemo, useState } from 'react'
import { Edition } from '../design'
import { changePct, clockTime, formatPaise, formatQty } from '../lib/format'
import type { SymbolInfo, Trade } from '../lib/types'
import { useMarket } from '../store/market'
import { boardTone, sparkPath } from './bigBoard'
import { Teleprinter } from '../terminal/Teleprinter'
import './bigboard.css'

/**
 * The Big Board (#/wall): a full-screen wall display for the projector, like
 * the boards on a real exchange floor. Five glowing tiles (green when a stock
 * is up on the day, red when down, brighter the bigger the move) pulse with
 * every trade, while the tape and the latest headline scroll underneath.
 */
export function BigBoard() {
  const symbols = useMarket((s) => s.symbols)
  const tapes = useMarket((s) => s.tapes)
  const wire = useMarket((s) => s.wire)
  const names = useMarket((s) => s.names)
  const connection = useMarket((s) => s.connection)
  const [now, setNow] = useState(() => new Date())
  const [full, setFull] = useState(false)

  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 1000)
    const onFs = () => setFull(!!document.fullscreenElement)
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && !document.fullscreenElement) location.hash = '#/floor' }
    document.addEventListener('fullscreenchange', onFs)
    window.addEventListener('keydown', onKey)
    return () => { clearInterval(t); document.removeEventListener('fullscreenchange', onFs); window.removeEventListener('keydown', onKey) }
  }, [])

  const tape = useMemo(() => Object.values(tapes).flat().sort((a, b) => b.id - a.id).slice(0, 24), [tapes])
  const lastPrice = useMarket((s) => s.lastPrice)
  const up = symbols.filter((s) => (lastPrice[s.symbol] ?? s.lastPrice) >= s.referencePrice).length

  const toggleFull = () => {
    if (document.fullscreenElement) void document.exitFullscreen()
    else void document.documentElement.requestFullscreen?.().catch(() => {})
  }

  return (
    <Edition edition="night" style={{ minHeight: '100vh' }}>
      <div className="dx-wall">
        <header className="dx-wall-top">
          <div className="dx-wall-brand"><strong>DalalX</strong><span>Big Board</span></div>
          <div className="dx-wall-breadth dx-num"><span className="is-up">▲ {up} up</span><span className="is-down">▼ {symbols.length - up} down</span></div>
          <div className="dx-wall-clock dx-num">{now.toLocaleTimeString('en-IN', { hour12: false })} IST</div>
          <div className="dx-wall-actions">
            <span className={`dx-wall-live${connection === 'live' ? ' is-live' : ''}`}>{connection === 'live' ? '● Live' : '○ Offline'}</span>
            <button type="button" className="dx-focus" onClick={toggleFull}>{full ? 'Exit full screen' : 'Full screen ⤢'}</button>
            <a href="#/floor" className="dx-focus">Back to the floor</a>
          </div>
        </header>

        <main className="dx-wall-tiles">
          {symbols.map((s) => <Tile key={s.symbol} stock={s} trades={tapes[s.symbol] ?? []} />)}
          {!symbols.length && <p className="dx-wall-wait">Waiting for the exchange…</p>}
        </main>

        <footer className="dx-wall-foot">
          {wire[0] && <div className="dx-wall-news"><b>{wire[0].kicker}</b> <Teleprinter text={wire[0].text} /></div>}
          <div className="dx-wall-tape" aria-label="Latest trades">
            <div className="dx-wall-tape-track">
              {[0, 1].map((copy) => (
                <div key={copy} className="dx-wall-tape-run" aria-hidden={copy === 1}>
                  {tape.map((t) => <TapeItem key={`${copy}-${t.id}`} trade={t} names={names} />)}
                </div>
              ))}
            </div>
          </div>
        </footer>
      </div>
    </Edition>
  )
}

function Tile({ stock, trades }: { stock: SymbolInfo; trades: Trade[] }) {
  const lastPrice = useMarket((s) => s.lastPrice[stock.symbol] ?? stock.lastPrice)
  const status = useMarket((s) => s.status[stock.symbol]?.phase ?? 'OPEN')
  const pct = changePct(lastPrice, stock.referencePrice)
  const tone = boardTone(pct)
  const prices = trades.slice(0, 60).map((t) => t.price).reverse()
  const volume = trades.reduce((a, t) => a + t.qty, 0)
  const lastId = trades[0]?.id ?? 0
  const lastSide = trades[0]?.aggressorSide

  return (
    <article className={`dx-tile is-${tone.direction} is-${status.toLowerCase()}`}
      style={{ ['--glow' as string]: tone.strength.toFixed(2) }}>
      <span key={lastId} className={`dx-tile-pulse${lastSide === 'SELL' ? ' is-sell' : ''}`} aria-hidden="true" />
      <div className="dx-tile-head">
        <strong>{stock.symbol}</strong>
        {status !== 'OPEN' && <span className="dx-tile-badge">{status === 'HALTED' ? 'Halted' : 'Auction'}</span>}
      </div>
      <div className="dx-tile-name">{stock.name}</div>
      <div key={lastPrice} className={`dx-tile-price dx-num ${pct >= 0 ? 'dx-anim-ink-up' : 'dx-anim-ink-down'}`}>₹{formatPaise(lastPrice)}</div>
      <div className="dx-tile-change dx-num">{pct >= 0 ? '▲' : '▼'} {Math.abs(pct).toFixed(2)}%<span>{pct >= 0 ? '+' : '−'}₹{formatPaise(Math.abs(lastPrice - stock.referencePrice))}</span></div>
      <svg className="dx-tile-spark" viewBox="0 0 200 50" preserveAspectRatio="none" aria-hidden="true">
        <path d={sparkPath(prices, 200, 50, true)} className="dx-tile-spark-fill" />
        <path d={sparkPath(prices, 200, 50, false)} className="dx-tile-spark-line" />
      </svg>
      <div className="dx-tile-foot dx-num"><span>Vol {formatQty(volume)}</span><span>{trades.length} trades</span></div>
    </article>
  )
}

function TapeItem({ trade, names }: { trade: Trade; names: Record<string, string> }) {
  const buyer = names[trade.buyerId] ?? trade.buyerId
  const seller = names[trade.sellerId] ?? trade.sellerId
  return (
    <span className={`dx-wall-tape-item ${trade.aggressorSide === 'BUY' ? 'is-up' : 'is-down'}`}>
      <b>{trade.symbol}</b> {formatQty(trade.qty)} @ ₹{formatPaise(trade.price)}
      <small>{buyer} ← {seller} · {clockTime(trade.timestamp).slice(0, 8)}</small>
    </span>
  )
}
