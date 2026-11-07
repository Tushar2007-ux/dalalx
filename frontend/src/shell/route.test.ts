import { describe, expect, it } from 'vitest'
import { ROUTES, routeOf } from './route'

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

  it('knows the new pages, including the link-only ones', () => {
    expect(routeOf('#/engine')).toBe('engine')
    expect(routeOf('#/audience')).toBe('audience')
    expect(routeOf('#/how')).toBe('how')
    expect(routeOf('#/wall')).toBe('wall')
    expect(routeOf('#/join')).toBe('join')
  })

  it('keeps the phone page and big board out of the section bar', () => {
    const hashes = ROUTES.map((r) => r.hash)
    expect(hashes).not.toContain('#/join')
    expect(hashes).not.toContain('#/wall')
  })
})
