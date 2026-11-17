import type {
  AccountSession, AccountStatement, AmendResponse, BotRecipe, DayReport, JournalInfo, RewindView, TournamentStandings,
  Board, EngineSnapshot, GuestOrder, GuestView, Invite, Joined, Portfolio, RushResult,
  BookSnapshot, BotInfo, BotStyle, Candle, Case, RuleInfo, Storylines, WireHeadline, XRay, CancelResponse, Interval, OrderResult, PlaceOrder, SymbolInfo, Trade,
} from './types'

/**
 * Thin REST client for the Spring Boot server. Relative URLs go through the
 * Vite proxy (vite.config.ts) in development, so the browser sees one origin.
 * Every error the server sends is {"error": "..."}; we surface that message.
 */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

/** Where the browser keeps a signed-in account's session token. */
export const SESSION_KEY = 'uncross-session'

/** The signed-in account's token, if any (reading storage can fail in private mode). */
export function sessionToken(): string | null {
  try { return localStorage.getItem(SESSION_KEY) } catch { return null }
}

/** Every request carries the session header when someone is signed in, so their orders trade from their account. */
function withSession(init?: RequestInit): RequestInit | undefined {
  const token = sessionToken()
  if (!token) return init
  return { ...init, headers: { ...(init?.headers as Record<string, string> | undefined), 'X-Uncross-Session': token } }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, withSession(init))
  if (!response.ok) {
    let message = `HTTP ${response.status}`
    try {
      const body = (await response.json()) as { error?: string }
      if (body.error) message = body.error
    } catch {
      // no JSON body: keep the HTTP status as the message
    }
    throw new ApiError(response.status, message)
  }
  return (await response.json()) as T
}

/** For endpoints that answer with an empty body (204, or 200 with nothing). */
async function requestNoBody(url: string, init?: RequestInit): Promise<void> {
  const response = await fetch(url, withSession(init))
  if (!response.ok) throw new ApiError(response.status, `HTTP ${response.status}`)
}

const json = (body: unknown): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const api = {
  health: () => request<{ status: string }>('/api/health'),
  symbols: () => request<SymbolInfo[]>('/api/symbols'),
  book: (symbol: string, depth = 10) => request<BookSnapshot>(`/api/books/${symbol}?depth=${depth}`),
  trades: (symbol: string, limit = 50) => request<Trade[]>(`/api/trades/${symbol}?limit=${limit}`),
  candles: (symbol: string, interval: Interval) => request<Candle[]>(`/api/candles/${symbol}?interval=${interval}`),
  placeOrder: (order: PlaceOrder) => request<OrderResult>('/api/orders', json(order)),
  cancelOrder: (orderId: number) => request<CancelResponse>(`/api/orders/${orderId}`, { method: 'DELETE' }),
  amendOrder: (orderId: number, price: number, qty: number) =>
    request<AmendResponse>(`/api/orders/${orderId}`, { ...json({ price, qty }), method: 'PATCH' }),
  listStock: (symbol: string, name: string, referencePrice: number) =>
    request<SymbolInfo>('/api/symbols', json({ symbol, name, referencePrice })),
  openAccount: (name: string, pin: string) => request<AccountSession>('/api/accounts', json({ name, pin })),
  signIn: (name: string, pin: string) => request<AccountSession>('/api/accounts/session', json({ name, pin })),
  signOut: () => requestNoBody('/api/accounts/session', { method: 'DELETE' }),
  account: () => request<AccountStatement>('/api/accounts/me'),
  buildBot: (recipe: BotRecipe) => request<BotInfo>('/api/bots/rules', json(recipe)),
  tournament: () => request<TournamentStandings>('/api/bots/tournament'),
  startTournament: (seconds: number) => request<TournamentStandings>('/api/bots/tournament', json({ seconds })),
  cancelTournament: () => requestNoBody('/api/bots/tournament', { method: 'DELETE' }),
  newsDesk: () => request<{ enabled: boolean }>('/api/news/desk'),
  setNewsDesk: (enabled: boolean) => request<{ enabled: boolean }>('/api/news/desk', { ...json({ enabled }), method: 'PUT' }),
  breakNews: () => requestNoBody('/api/news/break', { method: 'POST' }),
  day: () => request<DayReport>('/api/day'),
  journal: () => request<JournalInfo>('/api/journal'),
  startRewind: (session: string, speed: number) => request<RewindView>('/api/rewind', json({ session, speed })),
  rewind: () => request<RewindView>('/api/rewind'),
  pauseRewind: () => request<RewindView>('/api/rewind/pause', { method: 'POST' }),
  resumeRewind: () => request<RewindView>('/api/rewind/resume', { method: 'POST' }),
  rewindSpeed: (speed: number) => request<RewindView>('/api/rewind/speed', { ...json({ speed }), method: 'PUT' }),
  stopRewind: () => requestNoBody('/api/rewind', { method: 'DELETE' }),
  bots: () => request<BotInfo[]>('/api/bots'),
  setBotActive: (id: string, active: boolean) =>
    request<BotInfo>(`/api/bots/${id}/${active ? 'resume' : 'pause'}`, { method: 'POST' }),
  tuneBot: (id: string, aggressiveness: number, size: number) =>
    request<BotInfo>(`/api/bots/${id}/tuning`, { ...json({ aggressiveness, size }), method: 'PUT' }),
  hireBot: (name: string, style: BotStyle) => request<BotInfo>('/api/bots', json({ name, style })),
  removeBot: (id: string) => requestNoBody(`/api/bots/${id}`, { method: 'DELETE' }),
  wire: (limit = 20) => request<WireHeadline[]>(`/api/wire?limit=${limit}`),
  scenarios: () => request<Storylines>('/api/scenarios'),
  startScenario: (id: string) => request<unknown>(`/api/scenarios/${id}/start`, { method: 'POST' }),
  cases: () => request<Case[]>('/api/surveillance/cases'),
  dismissCase: (id: string) => request<Case>(`/api/surveillance/cases/${id}/dismiss`, { method: 'POST' }),
  suspendCase: (id: string) => request<Case>(`/api/surveillance/cases/${id}/suspend`, { method: 'POST' }),
  rules: () => request<RuleInfo[]>('/api/surveillance/rules'),
  setRule: (id: string, enabled: boolean) =>
    request<RuleInfo[]>(`/api/surveillance/rules/${id}`, { ...json({ enabled }), method: 'PUT' }),
  suspended: () => request<string[]>('/api/surveillance/suspended'),
  reinstate: (traderId: string) => request<string[]>(`/api/surveillance/traders/${traderId}/reinstate`, { method: 'POST' }),
  latestXRay: () => request<XRay>('/api/xray/latest'),
  xray: (orderId: number) => request<XRay>(`/api/xray/${orderId}`),
  recentXRays: (limit = 10) => request<XRay[]>(`/api/xray?limit=${limit}`),
  stopScenario: () => requestNoBody('/api/scenarios/stop', { method: 'POST' }),
  engine: () => request<EngineSnapshot>('/api/engine'),
  rush: () => request<RushResult>('/api/engine/rush', { method: 'POST' }),
  leaderboard: () => request<Board>('/api/leaderboard'),
  portfolio: () => request<Portfolio>('/api/portfolio'),
  invite: () => request<Invite>('/api/audience/invite'),
  join: (name: string) => request<Joined>('/api/audience/join', json({ name })),
  guestOrder: (token: string, order: GuestOrder) => request<OrderResult>('/api/audience/orders',
    { ...json(order), headers: { 'Content-Type': 'application/json', 'X-Guest-Token': token } }),
  guestCancelAll: (token: string) => request<number>('/api/audience/cancel-all',
    { method: 'POST', headers: { 'X-Guest-Token': token } }),
  me: (token: string) => request<GuestView>('/api/audience/me', { headers: { 'X-Guest-Token': token } }),
}
