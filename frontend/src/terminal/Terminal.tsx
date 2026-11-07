import { StockTabs } from '../design'
import './floor.css'
import { changePct } from '../lib/format'
import { Shell } from '../shell/Shell'
import { useMarket } from '../store/market'
import { BookPanel } from './BookPanel'
import { ChartPanel } from './ChartPanel'
import { FloorNow } from './FloorNow'
import { AuctionBand, AuctionStamp, HaltBand, HaltStamp } from './HaltBand'
import { bannerHeadline } from './headline'
import { MarketsPanel } from './MarketsPanel'
import { TicketPanel } from './TicketPanel'
import { PriceHero } from './PriceHero'
import { VivaTag } from '../viva/VivaTag'

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
  const book = useMarket((s) => s.books[s.selected])

  const info = symbols.find((x) => x.symbol === selected)
  const headline = bannerHeadline(wire, selected, lastPrice[selected], info?.referencePrice, connection, Date.now())
  const tabs = symbols.map((x) => ({
    symbol: x.symbol, name: x.name, change: changePct(lastPrice[x.symbol] ?? x.referencePrice, x.referencePrice),
    halted: status[x.symbol]?.phase === 'HALTED' || status[x.symbol]?.phase === 'AUCTION',
  }))

  return (
    <Shell route="floor" headline={headline} page={2} section="Markets" status={halted ? 'halted' : inAuction ? 'auction' : undefined}
      band={halted && selectedStatus ? <HaltBand symbol={selected} status={selectedStatus} />
        : inAuction && selectedStatus ? <AuctionBand status={selectedStatus} auction={auction} book={book} /> : undefined}>
      <div className="dx-tabsrow">
        <StockTabs tabs={tabs} value={selected} onChange={select} />
      </div>
      <div className="dx-herorow">
        <PriceHero />
        <FloorNow />
      </div>
      <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', flex: 1 }}>
        {(halted || inAuction) && <div className="dx-viva-row is-left"><VivaTag note={halted ? 'halt' : 'auction'} align="left" /></div>}
        {halted && <HaltStamp symbol={selected} />}
        {inAuction && <AuctionStamp symbol={selected} />}
        <div className="dx-floor" style={{
          filter: connection === 'offline' || halted ? 'grayscale(0.85) sepia(0.25) contrast(0.95)' : inAuction ? 'grayscale(0.6) sepia(0.15)' : 'none', transition: 'filter 900ms var(--ease-out)' }}>
          <div className="dx-floor-markets dx-card"><Tag note="markets" /><MarketsPanel /></div>
          <div className="dx-floor-chart dx-card"><Tag note="chart" /><ChartPanel /></div>
          <div className="dx-floor-book dx-card"><Tag note="book" /><BookPanel /></div>
          <div className="dx-floor-ticket dx-card"><Tag note="ticket" /><TicketPanel /></div>
        </div>
      </div>
    </Shell>
  )
}

/** A Viva-mode note in a panel's top-right corner (renders nothing when Viva mode is off). */
function Tag({ note }: { note: 'markets' | 'chart' | 'book' | 'ticket' }) {
  return <div className="dx-viva-row dx-viva-corner"><VivaTag note={note} /></div>
}
