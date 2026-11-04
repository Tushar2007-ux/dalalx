import { useCallback, useEffect, useId, useState, type ReactNode } from 'react'
import {
  AlertNotice, Button, Modal, Monogram, OrderTypeToggle, Portrait, PriceInput, RubberStamp, SectionLabel,
} from '../design'
import { api, ApiError } from '../lib/api'
import { formatPaise } from '../lib/format'
import { botKey } from '../lib/market'
import { monogramOf } from '../lib/monogram'
import type { BotInfo, BotStyle, Story, Storylines } from '../lib/types'
import { Shell } from '../shell/Shell'
import { useMarket } from '../store/market'
import type { Headline } from '../terminal/headline'

const label = { fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase' } as const
const link = { appearance: 'none', border: 'none', background: 'none', padding: 0, cursor: 'pointer', fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 14, color: 'var(--ink)', textDecoration: 'underline', textUnderlineOffset: 3 } as const
const italic = { margin: 0, fontFamily: 'var(--font-display)', fontStyle: 'italic' } as const

export const SIZE_WORDS = ['Small lots', 'Modest', 'Standard', 'Large', 'Block size']
export const AGGRESSION_WORDS = ['Patient', 'Measured', 'Active', 'Pushy', 'Relentless']

const STYLES: { value: BotStyle; label: string; hint: string }[] = [
  { value: 'CONTRARIAN', label: 'Contrarian', hint: 'Buys the fear, sells the cheer.' },
  { value: 'MOMENTUM', label: 'Momentum', hint: 'Follows the trend, a little later than everyone else.' },
  { value: 'MARKET_MAKER', label: 'Maker', hint: 'Quotes both sides and earns the spread.' },
  { value: 'NOISE', label: 'Noise', hint: 'Trades at random to keep the tape busy.' },
]

const POLL_MS = 2000
type BotKey = 'munim' | 'momentum' | 'panic' | 'whale' | 'spoofer' | 'inspector'


/**
 * "Our Correspondents": every bot as a byline card with its live numbers and
 * two dials, plus hiring, sending on leave, removing, and Today's Storylines
 * (scripted scenarios). Layout follows design/export/ui_kits/correspondents.
 */
export function Correspondents() {
  const connection = useMarket((s) => s.connection)
  const toast = useMarket((s) => s.toast)
  const [bots, setBots] = useState<BotInfo[]>([])
  const [stories, setStories] = useState<Storylines | null>(null)
  const [hiring, setHiring] = useState(false)
  const [asking, setAsking] = useState<Story | null>(null)

  const load = useCallback(() => {
    api.bots().then(setBots).catch(() => {})
    api.scenarios().then(setStories).catch(() => {})
  }, [])
  useEffect(() => {
    if (connection === 'offline') return
    load()
    const timer = setInterval(load, POLL_MS)
    return () => clearInterval(timer)
  }, [connection, load])

  const fail = (what: string) => (e: unknown) =>
    toast({ kind: 'rejected', title: what, detail: e instanceof ApiError ? e.message : 'The exchange did not answer' })
  const replace = (updated: BotInfo) => setBots((all) => all.map((b) => (b.id === updated.id ? updated : b)))

  const running = stories?.stories.find((s) => s.id === stories.running.id) ?? null
  const filing = bots.filter((b) => b.status === 'ACTIVE').length
  const headline: Headline = running
    ? { kicker: 'Stop Press', state: 'breaking', tone: running.tone === 'UP' ? 'up' : running.tone === 'DOWN' ? 'down' : 'neutral', headline: `Now running: ${running.headline}` }
    : { kicker: 'Staff', state: 'live', tone: 'neutral', headline: 'Our Correspondents: who is trading on the floor today', deck: 'Tune how hard each bot trades, send them on leave, or hire a new one.' }

  const runStory = (story: Story) => {
    setAsking(null)
    api.startScenario(story.id)
      .then(() => toast({ kind: 'info', title: `Now running: ${story.headline.split(':')[0]}`, detail: `${story.symbol === 'ALL' ? 'All stocks' : story.symbol} · ${Math.round(story.durationSeconds / 60 * 10) / 10} min` }))
      .then(() => setTimeout(load, 400))
      .catch(fail('Could not start the story'))
  }
  const stopStory = () => api.stopScenario().then(() => setTimeout(load, 400)).catch(fail('Could not stop the story'))

  return (
    <Shell route="correspondents" headline={headline} page={4} section="Correspondents">
      <div style={{ marginTop: 18, display: 'flex', alignItems: 'end', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ margin: 0, fontFamily: 'var(--font-display)', fontWeight: 800, fontSize: 40, lineHeight: 1, letterSpacing: '-0.01em' }}>Our Correspondents</h1>
          <div className="dx-num" style={{ fontSize: 12, color: 'var(--ink-muted)', marginTop: 6 }}>
            {bots.length} on staff · {filing} filing · {bots.length - filing} on leave
          </div>
        </div>
        <Button variant="primary" onClick={() => setHiring(true)} disabled={connection === 'offline'}>+ Hire a correspondent</Button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(380px, 100%), 1fr))', gap: '26px 28px', marginTop: 14 }}>
        {bots.map((b) => <BotCard key={b.id} bot={b} onChange={replace} onFail={fail} onRemoved={load} />)}
        <button type="button" onClick={() => setHiring(true)} className="dx-focus"
          style={{ appearance: 'none', borderRadius: 0, cursor: 'pointer', border: '1px dashed var(--rule-strong)', borderTop: '2px solid var(--rule-strong)', background: 'transparent', color: 'var(--ink)', minHeight: 260, display: 'grid', placeItems: 'center', textAlign: 'center', padding: 20 }}>
          <span style={{ display: 'grid', gap: 6 }}>
            <span style={{ ...label, color: 'var(--ink-muted)' }}>Situations vacant</span>
            <span style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 24 }}>Hire a correspondent</span>
            <span style={{ ...italic, fontSize: 14, color: 'var(--ink-muted)' }}>Add a bot with its own trading style →</span>
          </span>
        </button>
      </div>

      <div style={{ marginTop: 34 }}>
        <SectionLabel rule="double" meta={running ? <span style={{ color: 'var(--stamp)' }}>■ 1 story running</span> : 'One story at a time'}>Today’s Storylines</SectionLabel>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(260px, 100%), 1fr))', gap: '24px 28px', marginTop: 10 }}>
          {(stories?.stories ?? []).map((s) => (
            <StoryCard key={s.id} story={s} running={running?.id === s.id} onRun={() => setAsking(s)} onStop={stopStory} />
          ))}
        </div>
      </div>

      <HireDialog open={hiring} onClose={() => setHiring(false)} onHired={(b) => { setBots((all) => [...all, b]); setHiring(false) }} onFail={fail} />

      <Modal open={!!asking} kicker="Run this story" title={asking ? `${asking.headline.split(':')[0]}?` : ''} onClose={() => setAsking(null)} width={480}
        actions={<>
          <Button variant="secondary" onClick={() => setAsking(null)}>Not now</Button>
          <Button variant={asking?.tone === 'DOWN' ? 'danger' : 'primary'} onClick={() => asking && runStory(asking)}>Run the story</Button>
        </>}>
        {asking && <div style={{ display: 'grid', gap: 10 }}>
          <p style={{ ...italic, fontSize: 15 }}>{asking.deck}</p>
          <div className="dx-num" style={{ fontSize: 12.5, color: 'var(--ink-muted)' }}>{storyMeta(asking)}</div>
          {running && <AlertNotice severity="warning" title={`This stops ${running.headline.split(':')[0]}`}>Only one storyline runs at a time.</AlertNotice>}
          <p style={{ margin: 0, fontSize: 13 }}>Your open orders stay in the book. Prices will move quickly; check your limits.</p>
        </div>}
      </Modal>
    </Shell>
  )
}

export function storyMeta(s: Story): string {
  const minutes = s.durationSeconds / 60
  return `${minutes < 1 ? `${s.durationSeconds} s` : `${Math.round(minutes * 10) / 10} min`} · ${s.symbol === 'ALL' ? 'All stocks' : s.symbol}`
}

function BotCard({ bot, onChange, onFail, onRemoved }: {
  bot: BotInfo
  onChange: (b: BotInfo) => void
  onFail: (what: string) => (e: unknown) => void
  onRemoved: () => void
}) {
  const [confirm, setConfirm] = useState(false)
  const key = botKey(bot.id)
  const on = bot.status === 'ACTIVE'
  const pnl = bot.stats.pnl
  const tune = (aggressiveness: number, size: number) =>
    api.tuneBot(bot.id, aggressiveness, size).then(onChange).catch(onFail(`Could not retune ${bot.name}`))

  return (
    <article style={{ display: 'grid', gap: 10, alignContent: 'start', borderTop: '2px solid var(--rule-strong)', paddingTop: 12 }}>
      <div style={{ display: 'grid', gridTemplateColumns: '120px minmax(0,1fr)', gap: 14, alignItems: 'start' }}>
        <div style={{ opacity: on ? 1 : 0.6 }}>
          {key && key !== 'you'
            ? <Portrait bot={key as BotKey} size={120} />
            : <div style={{ width: 120, height: 120, display: 'grid', placeItems: 'center', border: '1px dashed var(--rule-strong)' }}><Monogram bot={monogramOf(bot.name)} size={56} /></div>}
        </div>
        <div style={{ minWidth: 0 }}>
          <div style={{ ...label, color: 'var(--ink-muted)' }}>By</div>
          <h3 style={{ margin: '1px 0 0', fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 23, lineHeight: 1.1 }}>{bot.name}</h3>
          <div style={{ ...label, fontWeight: 600, fontSize: 10.5, marginTop: 4, color: 'var(--ink-muted)' }}>{bot.role}</div>
          <p style={{ ...italic, margin: '6px 0 0', fontSize: 14.5, lineHeight: 1.35 }}>“{bot.line}”</p>
        </div>
      </div>
      <dl style={{ margin: 0, display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0,1fr))', gap: 10 }}>
        <Stat k="Status" v={<span style={{ color: on ? 'var(--buy)' : 'var(--ink-muted)', fontFamily: 'var(--font-ui)', fontWeight: 600 }}>{on ? '● Filing' : '○ On leave'}</span>} />
        <Stat k="Orders today" v={bot.stats.orders.toLocaleString('en-IN')} />
        <Stat k="P&L today" v={<span style={{ color: pnl >= 0 ? 'var(--buy)' : 'var(--sell)' }}>{pnl >= 0 ? '▲ ' : '▼ '}₹{formatPaise(Math.abs(pnl))}</span>} />
      </dl>
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1fr)', gap: 16 }}>
        <Dial label="Aggressiveness" value={bot.tuning.aggressiveness} words={AGGRESSION_WORDS} disabled={!on}
          onChange={(v) => void tune(v, bot.tuning.size)} />
        <Dial label="Order size" value={bot.tuning.size} words={SIZE_WORDS} disabled={!on}
          onChange={(v) => void tune(bot.tuning.aggressiveness, v)} />
      </div>
      <div style={{ display: 'flex', gap: 18, alignItems: 'baseline', borderTop: '1px solid var(--rule)', paddingTop: 8, minHeight: 22 }}>
        {confirm ? <>
          <span style={{ fontSize: 13 }}>Remove {bot.name} from the floor?</span>
          <button type="button" style={{ ...link, color: 'var(--sell)', fontStyle: 'normal', fontFamily: 'var(--font-ui)', fontWeight: 600, fontSize: 13 }}
            onClick={() => api.removeBot(bot.id).then(onRemoved).catch(onFail(`Could not remove ${bot.name}`))}>Remove</button>
          <button type="button" style={{ ...link, fontStyle: 'normal', fontFamily: 'var(--font-ui)', fontSize: 13 }} onClick={() => setConfirm(false)}>Keep</button>
        </> : <>
          <button type="button" style={link} className="dx-focus"
            onClick={() => api.setBotActive(bot.id, !on).then(onChange).catch(onFail(`Could not update ${bot.name}`))}>
            {on ? 'Send on leave' : 'Back to filing'}
          </button>
          <button type="button" style={{ ...link, color: 'var(--sell)' }} className="dx-focus" onClick={() => setConfirm(true)}>Remove</button>
        </>}
      </div>
    </article>
  )
}

function Stat({ k, v }: { k: string; v: ReactNode }) {
  return (
    <div style={{ borderTop: '1px solid var(--rule)', paddingTop: 4 }}>
      <dt style={{ ...label, fontSize: 10, fontWeight: 600, color: 'var(--ink-muted)' }}>{k}</dt>
      <dd className="dx-num" style={{ margin: 0, fontSize: 14, fontWeight: 500 }}>{v}</dd>
    </div>
  )
}

/** A five-step slider with words for each step. */
function Dial({ label: name, value, words, onChange, disabled }: {
  label: string; value: number; words: string[]; onChange: (v: number) => void; disabled?: boolean
}) {
  const id = useId()
  return (
    <div style={{ display: 'grid', gap: 3 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <label htmlFor={id} style={{ ...label, fontSize: 10, color: 'var(--ink-muted)' }}>{name}</label>
        <span style={{ fontSize: 12, fontWeight: 600 }}>{words[value]}</span>
      </div>
      <input id={id} type="range" min="0" max="4" step="1" value={value} disabled={disabled}
        onChange={(e) => onChange(Number(e.target.value))} aria-valuetext={words[value]} style={{ accentColor: 'var(--ink)' }} />
    </div>
  )
}

function StoryCard({ story, running, onRun, onStop }: { story: Story; running: boolean; onRun: () => void; onStop: () => void }) {
  return (
    <article style={{ display: 'grid', gap: 8, alignContent: 'start', borderTop: '2px solid var(--rule-strong)', paddingTop: 10, position: 'relative', minWidth: 0 }}>
      <span style={{ ...label, color: 'var(--accent)' }}>{story.kicker}</span>
      <h3 style={{ margin: 0, fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 22, lineHeight: 1.12 }}>
        {story.tone !== 'NEUTRAL' && <span aria-hidden="true" style={{ color: story.tone === 'UP' ? 'var(--buy)' : 'var(--sell)', fontSize: '0.65em', marginRight: 6, verticalAlign: '0.12em' }}>{story.tone === 'UP' ? '▲' : '▼'}</span>}
        {story.headline}
      </h3>
      <p style={{ ...italic, fontSize: 14.5, lineHeight: 1.35, color: 'var(--ink-muted)' }}>{story.deck}</p>
      <span className="dx-num" style={{ fontSize: 11.5, color: 'var(--ink-muted)' }}>{storyMeta(story)}</span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 4 }}>
        {running
          ? <Button variant="danger" size="sm" onClick={onStop}>■ Stop this story</Button>
          : <Button variant="secondary" size="sm" onClick={onRun}>Run this story →</Button>}
      </div>
      {running && <RubberStamp kind="stop-press" text="Now running" size="sm" angle={-6} animate slamKey={story.id} backed
        style={{ position: 'absolute', right: 0, top: 6 }} />}
    </article>
  )
}

function HireDialog({ open, onClose, onHired, onFail }: {
  open: boolean; onClose: () => void; onHired: (b: BotInfo) => void; onFail: (what: string) => (e: unknown) => void
}) {
  const [name, setName] = useState('The Contrarian')
  const [style, setStyle] = useState<BotStyle>('CONTRARIAN')
  const clean = name.trim()
  return (
    <Modal open={open} kicker="Situations vacant" title="Hire a correspondent" onClose={onClose} width={500}
      actions={<>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button variant="primary" disabled={!clean}
          onClick={() => api.hireBot(clean, style).then(onHired).catch(onFail(`Could not hire ${clean}`))}>
          {`Hire ${clean || 'correspondent'}`}
        </Button>
      </>}>
      <div style={{ display: 'grid', gap: 14 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '56px minmax(0,1fr)', gap: 12, alignItems: 'end' }}>
          <Monogram bot={monogramOf(name)} size={56} />
          <PriceInput label="Byline name" value={name} onChange={setName} inputMode="text" align="left" hint="Shown on the tape as a stamp monogram" />
        </div>
        <div style={{ display: 'grid', gap: 5 }}>
          <span style={label}>Trading style</span>
          <OrderTypeToggle value={style} onChange={(v) => setStyle(v as BotStyle)} options={STYLES} />
        </div>
      </div>
    </Modal>
  )
}
