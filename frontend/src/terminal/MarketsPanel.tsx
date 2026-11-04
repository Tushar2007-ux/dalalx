import { BreadthBar, PriceCell, RuledPanel, SectionLabel } from '../design'
import { changePct, toRupees } from '../lib/format'
import { useMarket } from '../store/market'

/** Left column: every listed stock with its last price and day change, plus market breadth. */
export function MarketsPanel() {
  const symbols = useMarket((s) => s.symbols)
  const lastPrice = useMarket((s) => s.lastPrice)
  const selected = useMarket((s) => s.selected)
  const select = useMarket((s) => s.select)

  const moves = symbols.map((x) => (lastPrice[x.symbol] ?? x.referencePrice) - x.referencePrice)
  const advancing = moves.filter((m) => m > 0).length
  const declining = moves.filter((m) => m < 0).length

  return (
    <RuledPanel label="Markets" meta={`${symbols.length} stocks`} state={symbols.length ? 'ready' : 'loading'}
      style={{ minWidth: 0, padding: '0 12px 0 0', flex: '0.8 1 230px' }}>
      {symbols.map((x) => {
        const price = lastPrice[x.symbol] ?? x.referencePrice
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
            <PriceCell value={toRupees(price)} />
            <span style={{ fontSize: 11, color: 'var(--ink-muted)' }}>{x.name}</span>
            <PriceCell value={toRupees(price)} change={toRupees(price - x.referencePrice)}
              changePct={changePct(price, x.referencePrice)} changeOnly size="sm" />
          </button>
        )
      })}
      <div style={{ marginTop: 14 }}>
        <SectionLabel under="hair">Market Breadth</SectionLabel>
        <BreadthBar advancing={advancing} declining={declining} unchanged={symbols.length - advancing - declining}
          halted={0} style={{ marginTop: 4 }} />
      </div>
    </RuledPanel>
  )
}
