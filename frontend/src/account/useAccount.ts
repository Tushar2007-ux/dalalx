import { useEffect } from 'react'
import { api, ApiError, SESSION_KEY, sessionToken } from '../lib/api'
import { useMarket } from '../store/market'

const POLL_MS = 3000

/**
 * Keeps the signed-in account's statement fresh: read once at start-up (the
 * browser remembers the session), then every few seconds and after each of
 * its trades. If the server no longer knows the session (it was restarted
 * without a journal, say), the browser forgets it and trades as the house desk.
 */
export function useAccount(): void {
  const traderId = useMarket((s) => s.account?.traderId)
  const lastFill = useMarket((s) => (traderId ? s.lastTradeBy[traderId]?.at : undefined))
  const connection = useMarket((s) => s.connection)

  useEffect(() => {
    if (connection !== 'live' || !sessionToken()) return
    let stopped = false
    const load = () => api.account()
      .then((a) => !stopped && useMarket.getState().setAccount(a))
      .catch((e) => {
        if (!stopped && e instanceof ApiError && e.status === 401) signOutLocally()
      })
    load()
    const timer = setInterval(load, POLL_MS)
    return () => { stopped = true; clearInterval(timer) }
  }, [connection, lastFill])
}

/** Remembers a new session in this browser. */
export function rememberSession(token: string): void {
  try { localStorage.setItem(SESSION_KEY, token) } catch { /* private mode: lasts until the tab closes */ }
}

/** Forgets the session here (the server is told separately). */
export function signOutLocally(): void {
  try { localStorage.removeItem(SESSION_KEY) } catch { /* nothing to forget */ }
  useMarket.getState().setAccount(null)
}
