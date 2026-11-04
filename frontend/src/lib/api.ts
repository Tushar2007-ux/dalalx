import type {
  BookSnapshot, Candle, CancelResponse, Interval, OrderResult, PlaceOrder, SymbolInfo, Trade,
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
}
