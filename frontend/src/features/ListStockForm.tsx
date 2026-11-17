import { useState } from 'react'
import { Button, PriceInput, SectionLabel } from '../design'
import { api, ApiError } from '../lib/api'
import { useMarket } from '../store/market'
import { checkListing } from './rules'

/**
 * Lists a new stock while the market runs. On the server that is one method,
 * {@code Exchange.list}: a new worker thread with its own queue, book and
 * circuit breaker. Here, a sixth press appears in the hall.
 */
export function ListStockForm({ onListed }: { onListed?: () => void }) {
  const symbols = useMarket((s) => s.symbols)
  const toast = useMarket((s) => s.toast)
  const [symbol, setSymbol] = useState('')
  const [name, setName] = useState('')
  const [price, setPrice] = useState('')
  const [busy, setBusy] = useState(false)
  const check = checkListing(symbol, name, price, symbols.map((s) => s.symbol))
  const touched = symbol !== '' || price !== ''

  const list = async () => {
    if ('error' in check && check.error) return
    const ok = check as { symbol: string; name: string; price: number }
    setBusy(true)
    try {
      await api.listStock(ok.symbol, ok.name, ok.price)
      useMarket.getState().setSymbols(await api.symbols())
      toast({ kind: 'info', title: `${ok.symbol} is listed`, detail: 'A new worker thread is running its book' })
      setSymbol(''); setName(''); setPrice('')
      onListed?.()
    } catch (e) {
      toast({ kind: 'rejected', title: 'Could not list the stock', detail: e instanceof ApiError ? e.message : 'The exchange did not answer' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="dx-listing">
      <SectionLabel rule="double" meta={`${symbols.length} of 12 presses`}>List a new stock</SectionLabel>
      <form className="dx-listing-form" onSubmit={(e) => { e.preventDefault(); void list() }}>
        <PriceInput label="Symbol" value={symbol} onChange={(v) => setSymbol(v.toUpperCase().slice(0, 12))} inputMode="text" align="left" placeholder="ITC" />
        <PriceInput label="Company" value={name} onChange={setName} inputMode="text" align="left" placeholder="ITC" />
        <PriceInput label="Opening price" prefix="₹" value={price} onChange={setPrice} inputMode="decimal" placeholder="450.00" />
        <Button type="submit" variant="primary" disabled={'error' in check && !!check.error} loading={busy}>List it</Button>
      </form>
      <p className="dx-listing-note">{touched && 'error' in check && check.error ? check.error
        : 'It gets its own worker thread, order book and circuit breaker at once. The bots start trading it too.'}</p>
    </section>
  )
}
