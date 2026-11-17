import { useState } from 'react'
import { AlertNotice, Button, OrderTypeToggle, PriceInput, SectionLabel } from '../design'
import { api, ApiError } from '../lib/api'
import { formatPaise, formatQty } from '../lib/format'
import { Shell } from '../shell/Shell'
import { useMarket } from '../store/market'
import { checkCredentials } from '../features/rules'
import { DataTable, Empty, Stat } from '../ui'
import { signedRupees } from '../audience/guest'
import { rememberSession, signOutLocally } from './useAccount'
import '../features/features.css'

const MODES = [
  { value: 'open', label: 'Open an account', hint: 'Start with ₹10,00,000 and no shares.' },
  { value: 'sign-in', label: 'Sign in', hint: 'Use the name and PIN you chose.' },
]

/**
 * "Your Account": sign in to trade with your own money, then read your
 * statement. The rules are the risk desk's: you cannot buy with money you do
 * not have, or sell shares you do not own, and a waiting order sets its money
 * (or shares) aside so it cannot be spent twice.
 */
export function AccountPage() {
  const account = useMarket((s) => s.account)
  return (
    <Shell route="account" page={9} section="Your Account" headline={{
      kicker: 'Accounts', tone: 'neutral', state: 'live',
      headline: account ? `${account.name}'s account` : 'Trade with your own ₹10,00,000',
      deck: account
        ? 'Every order you place from the ticket now trades from this account.'
        : 'Open an account to trade under your own name, with your own cash and your own limits.',
    }}>
      {account ? <Statement /> : <SignIn />}
    </Shell>
  )
}

function SignIn() {
  const [mode, setMode] = useState('open')
  const [name, setName] = useState('')
  const [pin, setPin] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const problem = checkCredentials(name, pin)

  const submit = async () => {
    if (problem) { setError(problem); return }
    setBusy(true)
    setError(null)
    try {
      const session = mode === 'open' ? await api.openAccount(name.trim(), pin) : await api.signIn(name.trim(), pin)
      rememberSession(session.token)
      useMarket.getState().setAccount(session.account)
      useMarket.getState().toast({ kind: 'info', title: `Signed in as ${session.account.name}`, detail: 'Your orders now trade from your account' })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'The exchange did not answer')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="dx-account">
      <form className="dx-account-form" onSubmit={(e) => { e.preventDefault(); void submit() }}>
        <OrderTypeToggle value={mode} onChange={setMode} options={MODES} />
        <PriceInput label="Your name" value={name} onChange={setName} inputMode="text" align="left" hint="2–20 letters or digits" />
        <PriceInput label="PIN" value={pin} onChange={(v) => setPin(v.replace(/\D/g, '').slice(0, 8))} inputMode="numeric" align="left"
          hint="4–8 digits. Stored only as a salted hash." />
        {error && <AlertNotice severity="warning" title="Not signed in">{error}</AlertNotice>}
        <Button type="submit" variant="primary" size="lg" fullWidth loading={busy}>
          {mode === 'open' ? 'Open my account' : 'Sign in'}
        </Button>
      </form>
      <aside className="dx-account-rules">
        <SectionLabel rule="double">The house rules</SectionLabel>
        <ol>
          <li><b>Cash first.</b> A buy needs the money now. A limit order sets aside its price × size; a market order sets aside the last price plus 10%.</li>
          <li><b>No short selling.</b> You can only sell shares you own, and a sell sets them aside until it fills or you cancel.</li>
          <li><b>Nothing is spent twice.</b> Money set aside for one order cannot be used for another until it comes back.</li>
          <li><b>Paper money only.</b> The ₹10,00,000 is simulated, and so is everything you buy with it.</li>
        </ol>
      </aside>
    </div>
  )
}

function Statement() {
  const account = useMarket((s) => s.account)!
  const [leaving, setLeaving] = useState(false)
  const signOut = async () => {
    setLeaving(true)
    try { await api.signOut() } catch { /* the browser forgets it either way */ }
    signOutLocally()
    setLeaving(false)
  }
  const tone = account.profit > 0 ? 'up' : account.profit < 0 ? 'down' : undefined
  return (
    <div className="dx-account-statement">
      <div className="dx-account-stats">
        <Stat label="Worth today" value={`₹${formatPaise(account.worth)}`} />
        <Stat label="Profit since opening" value={signedRupees(account.profit)} tone={tone} />
        <Stat label="Cash" value={`₹${formatPaise(account.cash)}`} size="sm" />
        <Stat label="Free to spend" value={`₹${formatPaise(account.availableCash)}`} size="sm" />
        <Stat label="Set aside for orders" value={`₹${formatPaise(account.cashSetAside)}`} size="sm" />
      </div>
      <SectionLabel rule="bold" meta={`${account.holdings.length} ${account.holdings.length === 1 ? 'stock' : 'stocks'}`}>Holdings</SectionLabel>
      {account.holdings.length
        ? <DataTable head={['Stock', 'Shares', 'Set aside to sell', 'Last price', 'Value']}>
            {account.holdings.map((h) => (
              <tr key={h.symbol}>
                <td>{h.symbol}</td>
                <td>{formatQty(h.shares)}</td>
                <td>{formatQty(h.setAside)}</td>
                <td>₹{formatPaise(h.lastPrice)}</td>
                <td>₹{formatPaise(h.value)}</td>
              </tr>
            ))}
          </DataTable>
        : <Empty>No shares yet. <a href="#/floor">Go to the trading floor</a> and buy something.</Empty>}
      <div style={{ display: 'flex', gap: 'var(--space-3)', marginTop: 'var(--space-4)', alignItems: 'center', flexWrap: 'wrap' }}>
        <Button variant="primary" onClick={() => { location.hash = '#/floor' }}>Trade from this account →</Button>
        <Button variant="secondary" loading={leaving} onClick={() => void signOut()}>Sign out</Button>
        <span className="dx-num" style={{ fontSize: 'var(--text-small)', color: 'var(--ink-muted)' }}>Trader id {account.traderId}</span>
      </div>
    </div>
  )
}
