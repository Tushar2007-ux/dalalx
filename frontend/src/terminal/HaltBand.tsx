import { AlertNotice, Countdown, PriceCell, RubberStamp, SectionLabel } from '../design'
import { clockTime, formatQty, toRupees } from '../lib/format'
import type { AuctionUpdate, BookSnapshot, MarketStatus } from '../lib/types'
import { AuctionCurves } from './AuctionCurves'
import { CountdownRing } from './CountdownRing'

const small = { fontSize: 12, color: 'var(--ink-muted)' } as const

/**
 * The band under the headline while the selected stock is halted: the
 * breaker's alert, a countdown to the reopen, and what happens next.
 */
export function HaltBand({ symbol, status }: { symbol: string; status: MarketStatus }) {
  const until = status.until ? Date.parse(status.until) : Date.now()
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(280px, 100%), 1fr))', gap: 24, padding: '12px 0 14px', borderBottom: '1px solid var(--rule-strong)', alignItems: 'start' }}>
      <AlertNotice severity="critical" label="Circuit breaker" fresh title={`Circuit breaker: trading in ${symbol} is halted`}
        time={status.since ? clockTime(status.since).slice(0, 8) : undefined}>
        {status.reason ?? 'The price moved too far, too fast.'} Your resting orders stay in the book and you can still cancel them.
      </AlertNotice>
      <div style={{ display: 'flex', gap: 18, alignItems: 'center' }}>
        <CountdownRing endsAt={until} label="halted" />
        <Countdown endsAt={until} label="Trading resumes in" note={`Reopens at ${clockTime(until).slice(0, 8)}`} size="lg" warnAt={10} finalAt={5} />
      </div>
      <div>
        <SectionLabel under="hair">What happens next</SectionLabel>
        <ol style={{ margin: 0, paddingLeft: 18, display: 'grid', gap: 5, fontSize: 13.5, lineHeight: 1.4 }}>
          <li><strong>Halt.</strong> <span style={small}>No new orders, no trades. Cancels still work.</span></li>
          <li><strong>Call auction</strong> <span className="dx-num" style={small}>from {clockTime(until).slice(0, 8)}</span>. <span style={small}>Limit orders only; an indicative price is published.</span></li>
          <li><strong>Reopen.</strong> <span style={small}>Everything that crosses trades at one price, then normal trading resumes.</span></li>
        </ol>
      </div>
    </div>
  )
}

/** The big STOP PRESS stamp slammed across the columns during a halt. */
export function HaltStamp({ symbol }: { symbol: string }) {
  return (
    <div aria-hidden="true" style={{ position: 'absolute', left: '50%', top: 200, zIndex: 30, pointerEvents: 'none', transform: 'translateX(-50%) scale(2.2)' }}>
      <RubberStamp kind="stop-press" size="lg" angle={-9} sub={`Circuit breaker · ${symbol}`} animate slamKey={`halt-${symbol}`} />
    </div>
  )
}

const kicker = { fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: 'var(--tracking-label)', textTransform: 'uppercase', color: 'var(--ink-muted)' } as const

/**
 * The band while the selected stock is in its call auction: the indicative
 * price and volume, the buy/sell interest, and a countdown to the uncross.
 */
export function AuctionBand({ status, auction, book }: { status: MarketStatus; auction?: AuctionUpdate; book?: BookSnapshot }) {
  const ends = status.until ? Date.parse(status.until) : Date.now()
  const buy = auction?.buyQty ?? 0
  const sell = auction?.sellQty ?? 0
  const buyShare = buy + sell ? (buy / (buy + sell)) * 100 : 50
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(260px, 100%), 1fr))', gap: 24, padding: '12px 0 14px', borderBottom: '1px solid var(--rule-strong)', alignItems: 'start' }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'auto auto', gap: '4px 28px', alignItems: 'end', justifyContent: 'start' }}>
        <span style={kicker}>Indicative price</span><span style={kicker}>Indicative volume</span>
        {auction?.price != null
          ? <PriceCell value={toRupees(auction.price)} size="xl" prefix="₹" align="left" />
          : <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 18 }}>No overlap yet</span>}
        <span className="dx-num" style={{ fontSize: 32, fontWeight: 500 }}>{formatQty(auction?.volume ?? 0)}</span>
      </div>
      <div style={{ display: 'grid', gap: 4 }}>
        <span style={kicker}>Interest at that price</span>
        <div aria-hidden="true" style={{ display: 'flex', height: 14, border: '1px solid var(--rule-strong)' }}>
          <span className="dx-depth" style={{ position: 'relative', width: `${buyShare}%`, color: 'var(--buy)', borderRight: '1.5px solid var(--buy)' }}><span className="dx-hatch" style={{ position: 'absolute', inset: 0, opacity: 0.55 }} /></span>
          <span style={{ position: 'relative', flex: 1, color: 'var(--sell)' }}><span className="dx-hatch" style={{ position: 'absolute', inset: 0, opacity: 0.55 }} /></span>
        </div>
        <div className="dx-num" style={{ display: 'flex', justifyContent: 'space-between', gap: 8, fontSize: 11.5 }}>
          <span style={{ color: 'var(--buy)' }}>▲ Buy {formatQty(buy)}</span><span style={{ color: 'var(--sell)' }}>▼ Sell {formatQty(sell)}</span>
        </div>
        <AuctionCurves book={book} auction={auction} />
      </div>
      <Countdown endsAt={ends} label="Auction closes in" note={`Single-price match at ${clockTime(ends).slice(0, 8)}`} size="lg" warnAt={10} finalAt={5} />
    </div>
  )
}

/** The fainter CALL AUCTION stamp over the columns. */
export function AuctionStamp({ symbol }: { symbol: string }) {
  return (
    <div aria-hidden="true" style={{ position: 'absolute', left: '50%', top: 200, zIndex: 30, pointerEvents: 'none', transform: 'translateX(-50%) scale(2.2)', opacity: 0.55 }}>
      <RubberStamp kind="stop-press" text="Call auction" size="lg" angle={-9} sub={`${symbol} · one price at the bell`} />
    </div>
  )
}
