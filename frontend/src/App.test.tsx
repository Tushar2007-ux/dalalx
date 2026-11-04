import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import App from './App'
import { useMarket } from './store/market'

// The real stream opens a WebSocket; in tests we only check the REST-driven page.
vi.mock('./lib/stream', () => ({
  connectStream: (h: { onConnection: (up: boolean) => void }) => {
    h.onConnection(true)
    return { watch: () => () => {}, close: () => {} }
  },
}))

const SYMBOLS = [
  { symbol: 'RELIANCE', name: 'Reliance Industries', referencePrice: 246000, lastPrice: 248000 },
  { symbol: 'TCS', name: 'Tata Consultancy Services', referencePrice: 409000, lastPrice: 409000 },
]

function serve(routes: Record<string, unknown>) {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    const key = Object.keys(routes).find((k) => url.startsWith(k))
    return key ? new Response(JSON.stringify(routes[key])) : new Response('{"error":"nope"}', { status: 404 })
  }))
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  useMarket.setState({ symbols: [], connection: 'connecting', lastPrice: {}, books: {}, tapes: {}, chart: null })
})

describe('trading terminal', () => {
  it('prints the market from the server', async () => {
    serve({
      '/api/symbols': SYMBOLS,
      '/api/books/RELIANCE': { symbol: 'RELIANCE', bids: [{ price: 247900, totalQty: 50, orderCount: 2 }], asks: [{ price: 248100, totalQty: 30, orderCount: 1 }] },
      '/api/trades/RELIANCE': [],
      '/api/candles/RELIANCE': [],
    })
    render(<App />)
    expect(await screen.findByText('RELIANCE rises 0.81% to ₹2,480.00')).toBeTruthy()
    expect(await screen.findByText('Spread ₹2.00')).toBeTruthy()
    expect(screen.getByTestId('connection').textContent).toContain('Live')
  })

  it('shows the market-closed edition when the server is down', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 503 })))
    render(<App />)
    expect(await screen.findByText(/The presses have stopped/)).toBeTruthy()
    expect(screen.getByText('Market closed · exchange offline')).toBeTruthy()
  })
})
