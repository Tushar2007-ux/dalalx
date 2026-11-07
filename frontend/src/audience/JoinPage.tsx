import { useEffect, useState } from 'react'
import { Edition } from '../design'
import { ApiError, api } from '../lib/api'
import { changePct, formatPaise, formatQty } from '../lib/format'
import type { GuestView, Joined, Side } from '../lib/types'
import { useMarket } from '../store/market'
import { loadGuest, saveGuest, signedRupees } from './guest'
import './audience.css'

const QUANTITIES = [1, 10, 50, 100]

/**
 * The phone page (#/join): what someone in the audience sees after scanning
 * the QR code. Join with a name, pick a stock, tap Buy or Sell (market
 * orders, so they trade at once), and watch your profit and rank.
 *
 * Orders carry the secret token from joining, so the server knows who sent
 * them. Prices come from the same live stream as the big screen.
 */
export function JoinPage() {
  const [guest, setGuest] = useState<Joined | null>(() => loadGuest())
  const edition = useMarket((s) => s.edition)

  return (
    <Edition edition={edition} style={{ minHeight: '100vh' }}>
      <div className="dx-phone">
      <header className="dx-phone-mast">
        <span>Audience Edition</span>
        <strong>DalalX</strong>
        <span>Paper money only</span>
      </header>
      {guest
        ? <Trade guest={guest} onLeave={() => { saveGuest(null); setGuest(null) }} />
        : <JoinForm onJoined={(g) => { saveGuest(g); setGuest(g) }} />}
      </div>
    </Edition>
  )
}

function JoinForm({ onJoined }: { onJoined: (g: Joined) => void }) {
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onJoined(await api.join(name))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not join')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="dx-phone-join" onSubmit={submit}>
      <h1>You’re on the trading floor.</h1>
      <p>Pick a name for the leaderboard. You’ll trade the same five stocks as the bots, with paper money.</p>
      <label htmlFor="dx-guest-name">Your name</label>
      <input id="dx-guest-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={20}
        autoComplete="nickname" autoFocus placeholder="e.g. Richa" className="dx-focus" />
      {error && <div className="dx-phone-error" role="alert">{error}</div>}
      <button type="submit" disabled={busy || name.trim().length < 2} className="dx-phone-cta dx-focus">{busy ? 'Joining…' : 'Join the floor'}</button>
    </form>
  )
}

function Trade({ guest, onLeave }: { guest: Joined; onLeave: () => void }) {
  const symbols = useMarket((s) => s.symbols)
  const lastPrice = useMarket((s) => s.lastPrice)
  const connection = useMarket((s) => s.connection)
  const [symbol, setSymbol] = useState('RELIANCE')
  const [qty, setQty] = useState(10)
  const [me, setMe] = useState<GuestView | null>(null)
  const [flash, setFlash] = useState<{ id: number; tone: 'up' | 'down' | 'bad'; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  // My score every two seconds (the leaderboard itself is pushed to everyone).
  useEffect(() => {
    let stopped = false
    const load = () => api.me(guest.token).then((m) => !stopped && setMe(m)).catch((e) => {
      if (e instanceof ApiError && e.status === 401) onLeave() // the server restarted: join again
    })
    load()
    const timer = setInterval(load, 2000)
    return () => { stopped = true; clearInterval(timer) }
  }, [guest.token, onLeave])

  const stock = symbols.find((s) => s.symbol === symbol)
  const price = lastPrice[symbol] ?? stock?.lastPrice
  const pct = price !== undefined && stock ? changePct(price, stock.referencePrice) : 0
  const phase = useMarket((s) => s.status[symbol]?.phase ?? 'OPEN')
  const held = me?.holdings.find((h) => h.symbol === symbol)

  const send = async (side: Side) => {
    setBusy(true)
    try {
      const r = await api.guestOrder(guest.token, { symbol, side, type: 'MARKET', qty })
      const filled = r.trades.reduce((a, t) => a + t.qty, 0)
      const avg = filled ? r.trades.reduce((a, t) => a + t.price * t.qty, 0) / filled : 0
      if (filled) {
        navigator.vibrate?.(25)
        setFlash({ id: Date.now(), tone: side === 'BUY' ? 'up' : 'down',
          text: `${side === 'BUY' ? 'Bought' : 'Sold'} ${formatQty(filled)} ${symbol} at ₹${formatPaise(Math.round(avg))}` })
      } else {
        setFlash({ id: Date.now(), tone: 'bad', text: r.reason ?? 'Nothing to trade against right now' })
      }
      api.me(guest.token).then(setMe).catch(() => {})
    } catch (e) {
      setFlash({ id: Date.now(), tone: 'bad', text: e instanceof Error ? e.message : 'Order failed' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="dx-phone-trade">
      <div className="dx-phone-me">
        <div>
          <small>Trading as</small>
          <b>{guest.name}</b>
        </div>
        <div className="dx-phone-rank">
          <small>Rank</small>
          <b className="dx-num">{me ? `#${me.rank}` : '…'}<span> of {me?.of ?? '…'}</span></b>
        </div>
        <div className="dx-phone-pnl">
          <small>Profit</small>
          <b className={`dx-num${(me?.stats.pnl ?? 0) > 0 ? ' is-up' : (me?.stats.pnl ?? 0) < 0 ? ' is-down' : ''}`}>{signedRupees(me?.stats.pnl ?? 0)}</b>
        </div>
      </div>

      <div className="dx-phone-stocks" role="tablist" aria-label="Stocks">
        {symbols.map((s) => {
          const p = lastPrice[s.symbol] ?? s.lastPrice
          const c = changePct(p, s.referencePrice)
          return (
            <button key={s.symbol} role="tab" aria-selected={s.symbol === symbol} className="dx-focus" onClick={() => setSymbol(s.symbol)}>
              <b>{s.symbol}</b>
              <span className={`dx-num ${c >= 0 ? 'is-up' : 'is-down'}`}>{c >= 0 ? '▲' : '▼'}{Math.abs(c).toFixed(2)}%</span>
            </button>
          )
        })}
      </div>

      <div className="dx-phone-quote">
        <small>{stock?.name ?? symbol}</small>
        <div key={price} className={`dx-phone-price dx-num ${pct >= 0 ? 'dx-anim-ink-up' : 'dx-anim-ink-down'}`}>₹{price !== undefined ? formatPaise(price) : '—'}</div>
        <div className={`dx-num ${pct >= 0 ? 'is-up' : 'is-down'}`}>{pct >= 0 ? '+' : ''}{pct.toFixed(2)}% today</div>
        {held && <div className="dx-phone-held dx-num">You hold {formatQty(held.qty)} @ ₹{formatPaise(held.avgPrice)} · {signedRupees(held.unrealised)}</div>}
      </div>

      <div className="dx-phone-qty" role="group" aria-label="Quantity">
        {QUANTITIES.map((q) => (
          <button key={q} type="button" aria-pressed={q === qty} className="dx-focus" onClick={() => setQty(q)}>{q}</button>
        ))}
      </div>

      {phase !== 'OPEN' && <div className="dx-phone-halt">{symbol} is {phase === 'HALTED' ? 'halted by the circuit breaker' : 'in its call auction'}. Market orders wait for the bell.</div>}

      <div className="dx-phone-actions">
        <button type="button" className="dx-phone-buy dx-focus" disabled={busy || connection !== 'live' || phase !== 'OPEN'} onClick={() => send('BUY')}>
          Buy {qty}
        </button>
        <button type="button" className="dx-phone-sell dx-focus" disabled={busy || connection !== 'live' || phase !== 'OPEN'} onClick={() => send('SELL')}>
          Sell {qty}
        </button>
      </div>

      {flash && <div key={flash.id} className={`dx-phone-flash is-${flash.tone}`} role="status">{flash.text}</div>}

      {me && me.holdings.length > 0 && (
        <div className="dx-phone-holdings">
          <small>Your portfolio</small>
          {me.holdings.map((h) => (
            <div key={h.symbol} className="dx-num">
              <b>{h.symbol}</b> <span>{formatQty(h.qty)} @ ₹{formatPaise(h.avgPrice)}</span>
              <span className={h.unrealised >= 0 ? 'is-up' : 'is-down'}>{signedRupees(h.unrealised)}</span>
            </div>
          ))}
        </div>
      )}

      <p className="dx-phone-foot">
        {connection === 'live' ? 'Live from the exchange' : 'Reconnecting…'} · Watch the big screen for the leaderboard ·{' '}
        <button type="button" className="dx-linkish" onClick={onLeave}>Leave</button>
      </p>
    </div>
  )
}
