import { describe, expect, it } from 'vitest'
import { routeOf } from './route'

describe('routeOf', () => {
  it('maps hashes to pages and falls back to the trading floor', () => {
    expect(routeOf('#/correspondents')).toBe('correspondents')
    expect(routeOf('#/xray')).toBe('xray')
    expect(routeOf('#/')).toBe('floor')
    expect(routeOf('')).toBe('floor')
    expect(routeOf('#/nowhere')).toBe('floor')
  })
})
