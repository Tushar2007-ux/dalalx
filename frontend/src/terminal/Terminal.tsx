import { StockTabs } from '../design'
import { changePct } from '../lib/format'
import { Shell } from '../shell/Shell'
import { useMarket } from '../store/market'
import { BookPanel } from './BookPanel'
import { ChartPanel } from './ChartPanel'
import { bannerHeadline } from './headline'
import { MarketsPanel } from './MarketsPanel'
import { TicketPanel } from './TicketPanel'

/**
 * The trading floor: stock tabs, then four newspaper columns (markets & wire
 * · chart & tape · order book · ticket). Layout follows
 * design/export/ui_kits/trading-floor-v2.
 */
export function Terminal() {
  const connection = useMarket((s) => s.connection)
  const symbols = useMarket((s) => s.symbols)
  const selected = useMarket((s) => s.selected)
  const select = useMarket((s) => s.select)
  const lastPrice = useMarket((s) => s.lastPrice)
  const wire = useMarket((s) => s.wire)

  const info = symbols.find((x) => x.symbol === selected)
  const headline = bannerHeadline(wire, selected, lastPrice[selected], info?.referencePrice, connection, Date.now())
  const tabs = symbols.map((x) => ({
    symbol: x.symbol, name: x.name, change: changePct(lastPrice[x.symbol] ?? x.referencePrice, x.referencePrice),
  }))

  return (
    <Shell route="floor" headline={headline} page={1} section="Markets">
      <div style={{ marginTop: 12 }}>
        <StockTabs tabs={tabs} value={selected} onChange={select} />
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'stretch', rowGap: 24, marginTop: 14, flex: 1,
        filter: connection === 'offline' ? 'grayscale(0.8) sepia(0.2)' : 'none', transition: 'filter 900ms var(--ease-out)' }}>
        <MarketsPanel />
        <ChartPanel />
        <BookPanel />
        <TicketPanel />
      </div>
    </Shell>
  )
}
