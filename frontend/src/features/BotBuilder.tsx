import { useState } from 'react'
import { Button, Modal, OrderTypeToggle, PriceInput, SideToggle } from '../design'
import { api, ApiError } from '../lib/api'
import type { BotInfo, BotTrigger } from '../lib/types'
import { useMarket } from '../store/market'
import { checkRecipe, describeRecipe, type RecipeForm } from './rules'

const TRIGGERS: { value: string; label: string; hint: string }[] = [
  { value: 'FALLS', label: 'Price falls', hint: 'Acts when the price drops this far from where it last acted.' },
  { value: 'RISES', label: 'Price rises', hint: 'Acts when the price climbs this far from where it last acted.' },
  { value: 'EVERY', label: 'On a timer', hint: 'Acts every so many seconds, whatever the price does.' },
]

/**
 * "Build your own bot": pick a stock, a trigger and an action, and the
 * exchange hires a bot that follows exactly that rule. It joins the floor
 * like any other bot (same runner, same ledger, same surveillance), and you
 * can see its trades on the tape and its profit on its card.
 */
export function BotBuilder({ open, onClose, onBuilt }: { open: boolean; onClose: () => void; onBuilt: (b: BotInfo) => void }) {
  const symbols = useMarket((s) => s.symbols)
  const toast = useMarket((s) => s.toast)
  const [form, setForm] = useState<RecipeForm>({
    name: 'Dip Buyer', symbol: 'TCS', when: 'FALLS', amountText: '0.5', side: 'BUY', qtyText: '100', maxText: '1000',
  })
  const [busy, setBusy] = useState(false)
  const check = checkRecipe(form)
  const set = (patch: Partial<RecipeForm>) => setForm((f) => ({ ...f, ...patch }))

  const build = async () => {
    if (!check.recipe) return
    setBusy(true)
    try {
      onBuilt(await api.buildBot(check.recipe))
      toast({ kind: 'info', title: `${check.recipe.name} is on the floor`, detail: describeRecipe(check.recipe) })
    } catch (e) {
      toast({ kind: 'rejected', title: 'Could not build the bot', detail: e instanceof ApiError ? e.message : 'The exchange did not answer' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} kicker="Workshop" title="Build your own bot" onClose={onClose} width={560}
      actions={<>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button variant="primary" disabled={!check.recipe} loading={busy} onClick={() => void build()}>Put it on the floor</Button>
      </>}>
      <div className="dx-builder">
        <PriceInput label="Name" value={form.name} onChange={(name) => set({ name })} inputMode="text" align="left" />
        <label className="dx-builder-field">
          <span className="dx-label">Watches</span>
          <select className="dx-focus" value={form.symbol} onChange={(e) => set({ symbol: e.target.value })}>
            {symbols.map((s) => <option key={s.symbol} value={s.symbol}>{s.symbol}</option>)}
          </select>
        </label>
        <div className="dx-builder-wide">
          <span className="dx-label">When</span>
          <OrderTypeToggle value={form.when} onChange={(v) => set({ when: v as BotTrigger, amountText: v === 'EVERY' ? '10' : '0.5' })} options={TRIGGERS} />
        </div>
        <PriceInput label={form.when === 'EVERY' ? 'Every' : 'By'} suffix={form.when === 'EVERY' ? 's' : '%'} value={form.amountText}
          onChange={(amountText) => set({ amountText })} inputMode="decimal" />
        <div className="dx-builder-field">
          <span className="dx-label">Then</span>
          <SideToggle value={form.side === 'BUY' ? 'buy' : 'sell'} onChange={(v) => set({ side: v === 'buy' ? 'BUY' : 'SELL' })} />
        </div>
        <PriceInput label="Shares each time" suffix="qty" value={form.qtyText} onChange={(qtyText) => set({ qtyText })} inputMode="numeric" />
        <PriceInput label="Never hold more than" suffix="qty" value={form.maxText} onChange={(maxText) => set({ maxText })} inputMode="numeric" />
        <p className={`dx-builder-preview${check.error ? ' is-error' : ''}`}>
          {check.recipe ? <>“{describeRecipe(check.recipe)}”</> : check.error}
        </p>
      </div>
    </Modal>
  )
}
