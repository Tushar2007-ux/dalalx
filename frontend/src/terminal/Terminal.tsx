import { StockTabs } from '../design'
import { changePct } from '../lib/format'
import { Shell } from '../shell/Shell'
import { useMarket } from '../store/market'
import { BookPanel } from './BookPanel'
import { ChartPanel } from './ChartPanel'
import { AuctionBand, AuctionStamp, HaltBand, HaltStamp } from './HaltBand'
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
  const status = useMarket((s) => s.status)
  const selectedStatus = status[selected]
  const halted = connection !== 'offline' && selectedStatus?.phase === 'HALTED'
  const inAuction = connection !== 'offline' && selectedStatus?.phase === 'AUCTION'
  const auction = useMarket((s) => s.auctions[s.selected])

  const info = symbols.find((x) => x.symbol === selected)
  const headline = bannerHeadline(wire, selected, lastPrice[selected], info?.referencePrice, connection, Date.now())
  const tabs = symbols.map((x) => ({
    symbol: x.symbol, name: x.name, change: changePct(lastPrice[x.symbol] ?? x.referencePrice, x.referencePrice),
    halted: status[x.symbol]?.phase === 'HALTED' || status[x.symbol]?.phase === 'AUCTION',
  }))

  return (
    <Shell route="floor" headline={headline} page={1} section="Markets" status={halted ? 'halted' : inAuction ? 'auction' : undefined}
      band={halted && selectedStatus ? <HaltBand symbol={selected} status={selectedStatus} />
        : inAuction && selectedStatus ? <AuctionBand status={selectedStatus} auction={auction} /> : undefined}>
      <div style={{ marginTop: 12 }}>
        <StockTabs tabs={tabs} value={selected} onChange={select} />
      </div>
      <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', flex: 1 }}>
        {halted && <HaltStamp symbol={selected} />}
        {inAuction && <AuctionStamp symbol={selected} />}
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'stretch', rowGap: 24, marginTop: 14, flex: 1,
          filter: connection === 'offline' || halted ? 'grayscale(0.85) sepia(0.25) contrast(0.95)' : inAuction ? 'grayscale(0.6) sepia(0.15)' : 'none', transition: 'filter 900ms var(--ease-out)' }}>
          <MarketsPanel />
          <ChartPanel />
          <BookPanel />
          <TicketPanel />
        </div>
      </div>
    </Shell>
  )
}
