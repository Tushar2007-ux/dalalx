import { describe, expect, it } from 'vitest'
import { routeOf } from './route'

describe('routeOf', () => {
  it('maps hashes to pages and falls back to the front page', () => {
    expect(routeOf('#/correspondents')).toBe('correspondents')
    expect(routeOf('#/xray')).toBe('xray')
    expect(routeOf('#/surveillance')).toBe('surveillance')
    expect(routeOf('#/floor')).toBe('floor')
    expect(routeOf('#/')).toBe('front')
    expect(routeOf('')).toBe('front')
    expect(routeOf('#/nowhere')).toBe('front')
    expect(routeOf('#/demo')).toBe('demo')
  })
})
