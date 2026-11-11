import { useState } from 'react'
import { SectionLabel } from '../design'
import { Shell } from '../shell/Shell'
import { Empty, Tag } from '../ui'
import { VivaTag } from '../viva/VivaTag'
import { START_BOOK, match, queueFor, type Kind, type Outcome, type Resting, type Side } from './how-it-works'
import './howitworks.css'

const KINDS: { kind: Kind; blurb: string }[] = [
  { kind: 'LIMIT', blurb: 'Trade at my price or better. Whatever is left waits in the book.' },
  { kind: 'MARKET', blurb: 'Trade now, at whatever price it takes. Nothing waits.' },
  { kind: 'IOC', blurb: 'Trade what you can this instant, then cancel the rest.' },
]

/**
 * How It Works: a stock exchange explained for someone who has never seen one,
 * with a working order book they can trade against.
 *
 * The book here is six orders and the matching runs in the browser, but it
 * follows the same three rules as the real engine next door, so what a reader
 * learns on this page is exactly what they will watch happen on the floor.
 */
export function HowItWorks() {
  return (
    <Shell route="how" page={8} section="How It Works" headline={{
      kicker: 'A reader’s guide', tone: 'neutral', state: 'live',
      headline: 'What a stock exchange actually does, in one page',
      deck: 'Buyers name the most they will pay, sellers the least they will take, and the exchange pairs them off. Everything else on this site is built on that one idea.',
    }}>
      <div className="dx-how">
        <section className="dx-how-intro dx-prose">
          <p className="dx-lead">
            A company is divided into millions of small pieces called <b>shares</b>. People want to buy and
            sell them, and an exchange is the place where those people meet. It owns nothing and sells
            nothing: its whole job is to pair buyers with sellers <b>fairly</b>, and the program that does
            the pairing is the <b>matching engine</b> — the Java code this project is built around.
          </p>
        </section>

        <Workshop />

        <section className="dx-how-rules">
          <SectionLabel rule="double" meta="The three rules the engine never breaks">Fair, by three rules</SectionLabel>
          <div className="dx-how-grid">
            <Rule n="1" title="Best price first">
              The buyer willing to pay most, and the seller willing to take least, are served before anyone
              else. In the book above, the ₹102 sellers trade before the ₹103 seller.
            </Rule>
            <Rule n="2" title="Then first come, first served">
              Two sellers both at ₹102? Whoever placed their order first trades first. Nobody can jump the
              queue by being bigger or louder — only by offering a better price.
            </Rule>
            <Rule n="3" title="You trade at their price">
              A trade happens at the price of the order that was already <i>waiting</i>. Offer ₹105 when
              someone is asking ₹102 and you pay ₹102: the saving is yours, not the exchange’s.
            </Rule>
          </div>
        </section>

        <section className="dx-how-safety">
          <SectionLabel rule="double" meta="What stops a market going wrong">The safety systems</SectionLabel>
          <div className="dx-how-grid">
            <Safety title="Circuit breaker" href="#/floor" cta="Watch one on the floor">
              If a stock moves 10% in a minute, trading in it <b>stops</b> for 30 seconds so nobody panics
              into a crash. Real exchanges do exactly this.
            </Safety>
            <Safety title="Call auction" href="#/floor" cta="See the curves">
              To reopen fairly, orders are collected without trading, then everything crosses at the single
              price that lets the <b>most shares</b> change hands.
            </Safety>
            <Safety title="Surveillance" href="#/surveillance" cta="Read a case file">
              An inspector watches every order for cheating — fake orders placed to mislead, trades with
              yourself — and can suspend a trader mid-session.
            </Safety>
            <Safety title="One thread per stock" href="#/engine" cta="Visit the Engine Room">
              Each stock is matched by its own thread, so five stocks match at once and no two threads ever
              touch the same book. That is why the code needs no locks.
            </Safety>
          </div>
        </section>

        <section className="dx-how-next">
          <SectionLabel rule="bold">Now try the real thing</SectionLabel>
          <div className="dx-how-next-row">
            <a className="dx-how-cta dx-focus" href="#/floor">Open the Trading Floor →</a>
            <a className="dx-how-cta dx-how-cta-quiet dx-focus" href="#/audience">Trade from your phone →</a>
          </div>
        </section>
      </div>
    </Shell>
  )
}

function Rule({ n, title, children }: { n: string; title: string; children: React.ReactNode }) {
  return (
    <article className="dx-how-rule">
      <span className="dx-how-rule-n" aria-hidden="true">{n}</span>
      <h3>{title}</h3>
      <p>{children}</p>
    </article>
  )
}

function Safety({ title, href, cta, children }: { title: string; href: string; cta: string; children: React.ReactNode }) {
  return (
    <article className="dx-how-safety-card">
      <h3>{title}</h3>
      <p>{children}</p>
      <a href={href} className="dx-focus">{cta} →</a>
    </article>
  )
}

/** The interactive half: send an order into a six-order book and read what happened. */
function Workshop() {
  const [side, setSide] = useState<Side>('BUY')
  const [kind, setKind] = useState<Kind>('LIMIT')
  const [qty, setQty] = useState(100)
  const [limit, setLimit] = useState(102)
  const [result, setResult] = useState<Outcome | null>(null)
  const [step, setStep] = useState(0)

  const book = result?.book ?? START_BOOK
  const asks = queueFor(book, 'SELL')
  const bids = queueFor(book, 'BUY')
  const run = () => {
    setResult(match(START_BOOK, side, kind, qty, limit))
    setStep(1)
  }
  const reset = () => { setResult(null); setStep(0) }
  const shownFills = result ? result.fills.slice(0, Math.max(0, step - 1)) : []
  const traded = shownFills.reduce((a, f) => a + f.qty, 0)

  return (
    <section className="dx-how-shop">
      <div className="dx-viva-row is-left"><VivaTag note="book" align="left" /></div>
      <SectionLabel rule="double" meta="Six orders, real rules, no server">Try it yourself</SectionLabel>

      <div className="dx-how-shop-grid">
        <div className="dx-how-book" aria-label="The order book">
          <div className="dx-how-book-head"><span>Sellers want</span><span>Qty</span><span>Who</span></div>
          {asks.slice().reverse().map((o) => <Row key={o.id} o={o} fills={shownFills} tone="sell" />)}
          <div className="dx-how-spread">
            {result && step > 0
              ? <b>{traded > 0 ? `${traded} shares traded` : 'nothing traded'}</b>
              : <b>₹101 bid · ₹102 ask</b>}
            <span>{result && step > 0 ? `step ${Math.min(step, result.story.length)} of ${result.story.length}` : 'the spread'}</span>
          </div>
          {bids.map((o) => <Row key={o.id} o={o} fills={shownFills} tone="buy" />)}
        </div>

        <form className="dx-how-ticket" onSubmit={(e) => { e.preventDefault(); run() }}>
          <div className="dx-label dx-muted">Your order</div>
          <div className="dx-how-sides">
            {(['BUY', 'SELL'] as Side[]).map((s) => (
              <button key={s} type="button" aria-pressed={side === s} className={`dx-how-side is-${s.toLowerCase()} dx-focus`}
                onClick={() => { setSide(s); reset() }}>{s === 'BUY' ? '▲ Buy' : '▼ Sell'}</button>
            ))}
          </div>
          <div className="dx-how-kinds">
            {KINDS.map((k) => (
              <button key={k.kind} type="button" aria-pressed={kind === k.kind} className="dx-how-kind dx-focus"
                onClick={() => { setKind(k.kind); reset() }}>{k.kind}</button>
            ))}
          </div>
          <p className="dx-how-kind-blurb">{KINDS.find((k) => k.kind === kind)!.blurb}</p>
          <label className="dx-how-field">
            <span className="dx-label dx-muted">Quantity</span>
            <input type="number" min={1} max={500} step={5} value={qty} className="dx-focus"
              onChange={(e) => { setQty(Math.max(1, Math.min(500, Number(e.target.value) || 1))); reset() }} />
          </label>
          <label className={`dx-how-field${kind === 'MARKET' ? ' is-off' : ''}`}>
            <span className="dx-label dx-muted">{kind === 'MARKET' ? 'No limit — market order' : side === 'BUY' ? 'Pay at most (₹)' : 'Take at least (₹)'}</span>
            <input type="number" min={95} max={110} step={0.5} value={limit} disabled={kind === 'MARKET'} className="dx-focus"
              onChange={(e) => { setLimit(Number(e.target.value) || 102); reset() }} />
          </label>
          <button type="submit" className="dx-how-send dx-focus">Send the order</button>
          {result && <button type="button" className="dx-how-again dx-focus" onClick={reset}>Put the book back</button>}
        </form>

        <div className="dx-how-story" aria-live="polite">
          <SectionLabel under="hair" meta={result ? <Tag tone={result.rest === 'rejected' ? 'alert' : undefined}>{result.rest === 'none' ? 'Filled' : result.rest === 'rests' ? 'Partly filled' : result.rest === 'rejected' ? 'Rejected' : 'Cancelled'}</Tag> : undefined}>
            What happened
          </SectionLabel>
          {!result && <Empty>Send an order and the engine will talk you through every step.</Empty>}
          {result && (
            <>
              <ol className="dx-how-steps">
                {result.story.slice(0, step).map((line, i) => (
                  <li key={i} className={i === step - 1 ? 'is-now' : undefined}>{line}</li>
                ))}
              </ol>
              {step < result.story.length
                ? <button type="button" className="dx-how-next-step dx-focus" onClick={() => setStep(step + 1)}>Next step →</button>
                : <p className="dx-how-done">
                    That is the whole of matching. The real engine does this hundreds of times a second,
                    on five stocks at once.
                  </p>}
            </>
          )}
        </div>
      </div>
    </section>
  )
}

function Row({ o, fills, tone }: { o: Resting; fills: { against: number; qty: number }[]; tone: 'buy' | 'sell' }) {
  const taken = fills.filter((f) => f.against === o.id).reduce((a, f) => a + f.qty, 0)
  const mine = o.who === 'You'
  return (
    <div className={`dx-how-row is-${tone}${taken ? ' is-hit' : ''}${mine ? ' is-mine' : ''}`}>
      <span className="dx-num">₹{o.price}</span>
      <span className="dx-num">
        {taken ? <><s>{o.qty + taken}</s> {o.qty}</> : o.qty}
      </span>
      <span>{o.who}</span>
    </div>
  )
}
