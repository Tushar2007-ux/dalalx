import { useEffect, useState } from 'react'
import { BotByline, SectionLabel } from '../design'
import { api } from '../lib/api'
import { formatPaise } from '../lib/format'
import { botKey } from '../lib/market'
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
      {bots.map((b) => (
        <BotByline key={b.id} bot={(botKey(b.id) ?? 'munim') as BotKey} layout="compact"
          status={!b.running || b.status === 'PAUSED' ? 'idle' : 'active'}
          stats={[pnlStat(b.stats.pnl), { label: 'Trades', value: b.stats.trades.toLocaleString('en-IN') }]}
          onClick={() => void toggle(b)} />
      ))}
    </div>
  )
}
