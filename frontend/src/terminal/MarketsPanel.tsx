import { BreadthBar, PriceCell, RuledPanel, SectionLabel } from '../design'
import { changePct, toRupees } from '../lib/format'
import { VivaTag } from '../viva/VivaTag'
import { WireColumn } from './WireColumn'
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
      style={{ minWidth: 0 }}>
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
            <span style={{ fontWeight: 600, fontSize: 'var(--text-small)', letterSpacing: '0.04em' }}>{x.symbol}</span>
            <PriceCell value={toRupees(price)} halted={halted} />
            <span style={{ fontSize: 'var(--text-label)', color: 'var(--ink-muted)' }}>{phase === 'HALTED' ? '■ Halted · circuit breaker' : phase === 'AUCTION' ? '■ Call auction' : x.name}</span>
            <PriceCell value={toRupees(price)} change={toRupees(price - x.referencePrice)}
              changePct={changePct(price, x.referencePrice)} changeOnly size="sm" />
          </button>
        )
      })}
      <div style={{ marginTop: 'var(--space-3)' }}>
        <SectionLabel meta="Newest first">The Wire</SectionLabel>
        <div className="dx-viva-row is-left"><VivaTag note="wire" align="left" /></div>
        {wire.length
          ? <WireColumn items={wire} />
          : <p style={{ margin: '6px 0', fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 'var(--text-ui)', color: 'var(--ink-muted)' }}>
              No news yet. Big moves, block trades and storylines will be printed here.</p>}
      </div>
      <div style={{ marginTop: 'var(--space-3)' }}>
        <SectionLabel under="hair">Market Breadth</SectionLabel>
        <BreadthBar advancing={advancing} declining={declining} unchanged={trading.length - advancing - declining}
          halted={symbols.length - trading.length} style={{ marginTop: 'var(--space-1)' }} />
      </div>
    </RuledPanel>
  )
}
