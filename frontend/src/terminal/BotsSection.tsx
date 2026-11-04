import { useEffect, useState } from 'react'
import { BotByline, Monogram, SectionLabel } from '../design'
import { api } from '../lib/api'
import { formatPaise } from '../lib/format'
import { botKey } from '../lib/market'
import { monogramOf } from '../lib/monogram'
import type { BotInfo } from '../lib/types'
import { useMarket } from '../store/market'

const POLL_MS = 2000

type BotKey = 'munim' | 'momentum' | 'panic' | 'whale' | 'spoofer' | 'inspector'

/** "+₹4,210.00" / "−₹610.00" with a direction for the byline's arrow. */
export function pnlStat(pnl: number): { label: string; value: string; dir?: 'up' | 'down' } {
  const sign = pnl > 0 ? '+' : pnl < 0 ? '−' : ''
  return { label: 'P&L', value: `${sign}₹${formatPaise(Math.abs(pnl))}`, dir: pnl > 0 ? 'up' : pnl < 0 ? 'down' : undefined }
}

/**
 * "On the Floor": every bot with its live P&L. Clicking a bot pauses or
 * resumes it (the server withdraws a paused bot's quotes). Polls every 2 s;
 * P&L changes slowly enough that a push stream would be overkill.
 */
export function BotsSection() {
  const connection = useMarket((s) => s.connection)
  const [bots, setBots] = useState<BotInfo[]>([])

  useEffect(() => {
    if (connection === 'offline') return
    let alive = true
    const load = () => api.bots().then((b) => alive && setBots(b)).catch(() => {})
    void load()
    const timer = setInterval(load, POLL_MS)
    return () => { alive = false; clearInterval(timer) }
  }, [connection])

  const toggle = async (bot: BotInfo) => {
    const updated = await api.setBotActive(bot.id, bot.status === 'PAUSED').catch(() => null)
    if (updated) setBots((all) => all.map((b) => (b.id === updated.id ? updated : b)))
  }

  const trading = bots.filter((b) => b.running && b.status === 'ACTIVE').length
  return (
    <div style={{ marginTop: 16 }}>
      <SectionLabel count={bots.length} meta={bots.length ? `${trading} trading · click to pause` : 'No bots'}>On the Floor</SectionLabel>
      {bots.map((b) => {
        const key = botKey(b.id)
        const status = !b.running || b.status === 'PAUSED' ? 'idle' : 'active'
        const stats = [pnlStat(b.stats.pnl), { label: 'Trades', value: b.stats.trades.toLocaleString('en-IN') }]
        return key && key !== 'you'
          ? <BotByline key={b.id} bot={key as BotKey} layout="compact" status={status} stats={stats} onClick={() => void toggle(b)} />
          : <HiredRow key={b.id} bot={b} active={status === 'active'} onClick={() => void toggle(b)} />
      })}
    </div>
  )
}

/** A correspondent hired on the Correspondents page has no portrait: a stamp monogram instead. */
function HiredRow({ bot, active, onClick }: { bot: BotInfo; active: boolean; onClick: () => void }) {
  const pnl = pnlStat(bot.stats.pnl)
  return (
    <button type="button" onClick={onClick} className="dx-focus"
      style={{ all: 'unset', boxSizing: 'border-box', width: '100%', cursor: 'pointer', display: 'grid', gridTemplateColumns: '40px minmax(0,1fr) auto', gap: 10, alignItems: 'center', padding: '6px 0', borderBottom: '1px solid var(--rule)' }}>
      <Monogram bot={monogramOf(bot.name)} size={36} />
      <span style={{ minWidth: 0 }}>
        <span style={{ display: 'block', fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 15 }}>{bot.name}</span>
        <span style={{ fontSize: 11.5, color: active ? 'var(--buy)' : 'var(--ink-muted)' }}>{active ? '●' : '○'} {bot.role} · {active ? 'Trading' : 'Paused'}</span>
      </span>
      <span className="dx-num" style={{ fontSize: 13, color: pnl.dir === 'up' ? 'var(--buy)' : pnl.dir === 'down' ? 'var(--sell)' : 'var(--ink)' }}>{pnl.value}</span>
    </button>
  )
}
