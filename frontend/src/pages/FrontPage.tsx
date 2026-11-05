import { BreadthBar, Portrait, PriceCell, RubberStamp, SectionLabel } from '../design'
import { changePct, formatPaise, toRupees } from '../lib/format'
import { Shell } from '../shell/Shell'
import { useMarket } from '../store/market'

const label = { fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase' } as const

const STORIES = [
  { title: 'Matching Engine', kick: 'Page 3', href: '#/xray', art: 'book', sum: 'Every order meets the book in price-time priority. Watch each fill happen, step by step, in X-Ray.' },
  { title: 'Bots With Personalities', kick: 'Page 4', href: '#/correspondents', art: 'whale', sum: 'A market maker, a momentum chaser, a panic seller, a whale and a spoofer, each trading in its own style.' },
  { title: 'The Circuit Breaker', kick: 'Page 2', href: '#/floor', art: 'stamp', sum: 'Fall 10% in a minute and trading stops. It reopens with a call auction at a single price.' },
  { title: 'Market Surveillance', kick: 'Page 5', href: '#/surveillance', art: 'inspector', sum: 'The Inspector reads the tape for spoofing, layering, wash trades and pumps, and can suspend a trader.' },
] as const

const STEPS = [
  ['You place an order', 'Buy or sell, at a limit price or at market. It arrives with a time stamp to the millisecond.'],
  ['The engine checks the book', 'Best price first; at the same price, whoever came first. Each match prints as a fill.'],
  ['The tape records it', 'Fills appear on the trade tape. Anything left over rests in the book until someone meets your price.'],
]

function Jump({ href, page, children }: { href: string; page: number; children: string }) {
  return (
    <a href={href} className="dx-focus" style={{ display: 'inline-grid', gap: 2, textDecoration: 'none', color: 'var(--ink)', borderTop: '2px solid var(--rule-strong)', borderBottom: '1px solid var(--rule-strong)', padding: '8px 2px 7px', minWidth: 220 }}>
      <span style={{ whiteSpace: 'nowrap', fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 22, lineHeight: 1.1 }}>{children} →</span>
      <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 13, color: 'var(--ink-muted)' }}>Continued on page {page}</span>
    </a>
  )
}

/** A live miniature of the RELIANCE book for the "Matching Engine" story. */
function MiniBook() {
  const book = useMarket((s) => s.books.RELIANCE)
  const asks = (book?.asks ?? []).slice(0, 2).reverse()
  const bids = (book?.bids ?? []).slice(0, 2)
  const max = Math.max(1, ...asks.map((l) => l.totalQty), ...bids.map((l) => l.totalQty))
  const row = (side: 'bid' | 'ask') => (l: { price: number; totalQty: number }) => (
    <div key={side + l.price} style={{ display: 'grid', gridTemplateColumns: '14px minmax(0,1fr) minmax(0,1fr) 64px', columnGap: 10, alignItems: 'center', height: 24, borderBottom: '1px solid var(--rule)' }}>
      <span aria-hidden="true" style={{ fontSize: 9, color: side === 'bid' ? 'var(--buy)' : 'var(--sell)' }}>{side === 'bid' ? '▲' : '▼'}</span>
      <span className="dx-num" style={{ textAlign: 'right', fontSize: 13, color: side === 'bid' ? 'var(--buy)' : 'var(--sell)' }}>{formatPaise(l.price)}</span>
      <span className="dx-num" style={{ textAlign: 'right', fontSize: 13 }}>{l.totalQty.toLocaleString('en-IN')}</span>
      <span aria-hidden="true" style={{ height: 12, position: 'relative', borderLeft: '1px solid var(--rule)' }}>
        <span className="dx-hatch" style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: `${(l.totalQty / max) * 100}%`, color: side === 'bid' ? 'var(--buy)' : 'var(--sell)' }} />
      </span>
    </div>
  )
  return (
    <div style={{ display: 'grid', alignContent: 'center', height: 150, padding: '0 4px' }}>
      {asks.map(row('ask'))}
      <div className="dx-num" style={{ fontSize: 10.5, color: 'var(--ink-muted)', textAlign: 'center', padding: '2px 0' }}>
        {asks.length && bids.length ? `spread ₹${formatPaise(book!.asks[0].price - bids[0].price)}` : 'RELIANCE · live'}
      </div>
      {bids.map(row('bid'))}
    </div>
  )
}

function Art({ kind }: { kind: (typeof STORIES)[number]['art'] }) {
  if (kind === 'book') return <MiniBook />
  if (kind === 'stamp') return <div style={{ height: 150, display: 'grid', placeItems: 'center' }}><RubberStamp kind="stop-press" size="lg" angle={-8} sub="Circuit breaker" /></div>
  return <div style={{ height: 150, display: 'grid', placeItems: 'center' }}><Portrait bot={kind} size={150} /></div>
}

/**
 * The front page: the big headline, the two ways in (the terminal and X-Ray),
 * the demo autopilot, live prices, the four features as stories, and how an
 * order is matched. Layout follows design/export/ui_kits/landing.
 */
export function FrontPage() {
  const symbols = useMarket((s) => s.symbols)
  const lastPrice = useMarket((s) => s.lastPrice)
  const status = useMarket((s) => s.status)
  const open = symbols.filter((x) => (status[x.symbol]?.phase ?? 'OPEN') === 'OPEN')
  const moves = open.map((x) => (lastPrice[x.symbol] ?? x.referencePrice) - x.referencePrice)
  const advancing = moves.filter((m) => m > 0).length
  const declining = moves.filter((m) => m < 0).length
  const colRule = { borderTop: '1px solid var(--rule)', paddingTop: 10 }

  return (
    <Shell route="front" full page={1} section="Front Page">
      <div style={{ maxWidth: 1320, width: '100%', margin: '0 auto' }}>
        <h1 style={{ margin: '26px 0 18px', fontFamily: 'var(--font-display)', fontWeight: 800, fontSize: 'clamp(48px, 7.4vw, 108px)', lineHeight: 0.95, letterSpacing: '-0.02em', textAlign: 'center', textWrap: 'balance' }}>
          WATCH A STOCK MARKET THINK
        </h1>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(280px, 100%), 1fr))', gap: '24px 32px', borderTop: '3px double var(--rule-strong)', paddingTop: 18 }}>
          <div style={{ display: 'grid', gap: 18, alignContent: 'start', ...colRule }}>
            <p style={{ margin: 0, fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 24, lineHeight: 1.3 }}>
              A live exchange where every order is matched in plain sight, by bots with personalities, under the eye of a surveillance desk.
            </p>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16 }}>
              <Jump href="#/floor" page={2}>Open the terminal</Jump>
              <Jump href="#/xray" page={3}>See how matching works</Jump>
            </div>
            <a href="#/demo" className="dx-focus" style={{ display: 'grid', gridTemplateColumns: 'auto minmax(0,1fr)', gap: 14, alignItems: 'center', textDecoration: 'none', color: 'var(--ink-inverse)', background: 'var(--ink)', padding: '12px 16px' }}>
              <span aria-hidden="true" style={{ fontSize: 22 }}>▶</span>
              <span>
                <span style={{ ...label, display: 'block', opacity: 0.9 }}>Demo autopilot</span>
                <span style={{ fontFamily: 'var(--font-display)', fontWeight: 600, fontSize: 18 }}>Read today’s story: a guided tour of about four minutes</span>
              </span>
            </a>
          </div>
          <figure style={{ margin: 0, ...colRule }}>
            <Portrait bot="munim" size={300} style={{ width: '100%', height: 'auto', aspectRatio: '1', maxWidth: 360 }} />
            <figcaption style={{ marginTop: 8, fontSize: 12.5, color: 'var(--ink-muted)', borderTop: '1px solid var(--rule)', paddingTop: 6 }}>
              <strong style={{ color: 'var(--ink)' }}>The Munim</strong>, the market maker, has quoted both sides of every stock since the opening bell.
            </figcaption>
          </figure>
          <div style={colRule}>
            <SectionLabel meta="Live">Markets at a glance</SectionLabel>
            {symbols.map((x) => {
              const price = lastPrice[x.symbol] ?? x.referencePrice
              const halted = (status[x.symbol]?.phase ?? 'OPEN') !== 'OPEN'
              return (
                <a key={x.symbol} href="#/floor" onClick={() => useMarket.getState().select(x.symbol)}
                  style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) auto auto', gap: 12, alignItems: 'baseline', padding: '7px 0', borderBottom: '1px solid var(--rule)', textDecoration: 'none', color: 'var(--ink)' }}>
                  <span style={{ fontWeight: 600, fontSize: 13, letterSpacing: '0.04em', color: halted ? 'var(--ink-muted)' : 'var(--ink)' }}>{x.symbol}</span>
                  <PriceCell value={toRupees(price)} halted={halted} />
                  <PriceCell value={toRupees(price)} change={toRupees(price - x.referencePrice)} changePct={changePct(price, x.referencePrice)} changeOnly size="sm" />
                </a>
              )
            })}
            <BreadthBar advancing={advancing} declining={declining} unchanged={open.length - advancing - declining}
              halted={symbols.length - open.length} style={{ marginTop: 12 }} />
          </div>
        </div>

        <div style={{ marginTop: 34 }}>
          <SectionLabel rule="double" meta="Four stories inside">In this edition</SectionLabel>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(240px, 100%), 1fr))', gap: '28px 32px', marginTop: 12 }}>
            {STORIES.map((e) => (
              <article key={e.title} style={{ display: 'grid', gap: 8, alignContent: 'start', borderTop: '1px solid var(--rule)', paddingTop: 10 }}>
                <Art kind={e.art} />
                <span style={{ ...label, color: 'var(--accent)' }}>{e.kick}</span>
                <h3 style={{ margin: 0, fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 23, lineHeight: 1.1 }}>{e.title}</h3>
                <p style={{ margin: 0, fontSize: 14, lineHeight: 1.45 }}>{e.sum}</p>
                <a href={e.href} style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 14 }}>Continued on {e.kick.toLowerCase()} →</a>
              </article>
            ))}
          </div>
        </div>

        <div style={{ marginTop: 34 }}>
          <SectionLabel rule="double" meta={<a href="#/xray" style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic' }}>See it in X-Ray →</a>}>How your order gets matched</SectionLabel>
          <ol style={{ listStyle: 'none', margin: '12px 0 0', padding: 0, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(260px, 100%), 1fr))' }}>
            {STEPS.map(([title, body], i) => (
              <li key={title} style={{ display: 'grid', gridTemplateColumns: 'auto minmax(0,1fr)', gap: '4px 12px', padding: '4px 20px 4px 0' }}>
                <span style={{ fontFamily: 'var(--font-display)', fontSize: 40, lineHeight: 1, color: 'var(--pencil-text)', gridRow: 'span 2' }}>{['①', '②', '③'][i]}</span>
                <span style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 19, lineHeight: 1.2 }}>{title}</span>
                <span style={{ fontSize: 14, lineHeight: 1.45 }}>{body}</span>
              </li>
            ))}
          </ol>
        </div>

        <footer style={{ marginTop: 40, display: 'grid', gap: 8, textAlign: 'center', borderTop: '4px double var(--rule-strong)', paddingTop: 12 }}>
          <span style={{ fontFamily: 'var(--font-display)', fontWeight: 800, fontSize: 22 }}>DalalX</span>
          <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 14 }}>Printed with Java 21, Spring Boot and React · An IIIT Allahabad OOM project</span>
        </footer>
      </div>
    </Shell>
  )
}
