import type {
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

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
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
  const response = await fetch(url, init)
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
}
