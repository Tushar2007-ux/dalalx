import { BreadthBar, PriceCell, RuledPanel, SectionLabel, TheWire } from '../design'
import { hhmm } from '../lib/format'
import { changePct, toRupees } from '../lib/format'
import { useMarket } from '../store/market'

/** Left column: every listed stock with its last price and day change, The Wire, and market breadth. */
export function MarketsPanel() {
  const symbols = useMarket((s) => s.symbols)
  const lastPrice = useMarket((s) => s.lastPrice)
  const selected = useMarket((s) => s.selected)
  const select = useMarket((s) => s.select)
  const wire = useMarket((s) => s.wire)
  const status = useMarket((s) => s.status)

  const trading = symbols.filter((x) => (status[x.symbol]?.phase ?? 'OPEN') === 'OPEN')
  const moves = trading.map((x) => (lastPrice[x.symbol] ?? x.referencePrice) - x.referencePrice)
  const advancing = moves.filter((m) => m > 0).length
  const declining = moves.filter((m) => m < 0).length

  return (
    <RuledPanel label="Markets" meta={`${symbols.length} stocks`} state={symbols.length ? 'ready' : 'loading'}
      style={{ minWidth: 0, padding: '0 12px 0 0', flex: '0.8 1 230px' }}>
      {symbols.map((x) => {
        const price = lastPrice[x.symbol] ?? x.referencePrice
        const phase = status[x.symbol]?.phase
        const halted = phase === 'HALTED' || phase === 'AUCTION'
        return (
          <button key={x.symbol} type="button" className="dx-focus" onClick={() => select(x.symbol)}
            aria-pressed={x.symbol === selected}
            style={{
              all: 'unset', boxSizing: 'border-box', width: '100%', cursor: 'pointer', display: 'grid',
              gridTemplateColumns: '1fr auto', alignItems: 'baseline', padding: '6px 4px',
              borderBottom: '1px solid var(--rule)',
              background: x.symbol === selected ? 'var(--surface-sunk)' : 'transparent',
            }}>
            <span style={{ fontWeight: 600, fontSize: 12.5, letterSpacing: '0.04em' }}>{x.symbol}</span>
            <PriceCell value={toRupees(price)} halted={halted} />
            <span style={{ fontSize: 11, color: 'var(--ink-muted)' }}>{phase === 'HALTED' ? '■ Halted · circuit breaker' : phase === 'AUCTION' ? '■ Call auction' : x.name}</span>
            <PriceCell value={toRupees(price)} change={toRupees(price - x.referencePrice)}
              changePct={changePct(price, x.referencePrice)} changeOnly size="sm" />
          </button>
        )
      })}
      <div style={{ marginTop: 14 }}>
        <SectionLabel meta="Newest first">The Wire</SectionLabel>
        {wire.length
          ? <TheWire items={wire.slice(0, 6).map((h, i) => ({
              id: h.id, time: hhmm(new Date(h.time)), headline: h.text, fresh: i === 0,
              tone: h.tone === 'UP' ? 'up' : h.tone === 'DOWN' ? 'down' : h.tone === 'ALERT' ? 'alert' : undefined,
            }))} />
          : <p style={{ margin: '6px 0', fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 13, color: 'var(--ink-muted)' }}>
              No news yet. Big moves, block trades and storylines will be printed here.</p>}
      </div>
      <div style={{ marginTop: 14 }}>
        <SectionLabel under="hair">Market Breadth</SectionLabel>
        <BreadthBar advancing={advancing} declining={declining} unchanged={trading.length - advancing - declining}
          halted={symbols.length - trading.length} style={{ marginTop: 4 }} />
      </div>
    </RuledPanel>
  )
}
