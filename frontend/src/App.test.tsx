import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import App from './App'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('shows the status returned by /api/health', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ status: 'ok' }))))
    render(<App />)
    expect(await screen.findByText('ok')).toBeTruthy()
  })

  it('reports when the server is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 503 })))
    render(<App />)
    expect(await screen.findByText('unreachable (HTTP 503)')).toBeTruthy()
  })
})
