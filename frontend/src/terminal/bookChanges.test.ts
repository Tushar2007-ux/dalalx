import { describe, expect, it } from 'vitest'
import { sideChanges } from './bookChanges'

const lv = (price: number, totalQty: number) => ({ price, totalQty, orderCount: 1 })

describe('book changes', () => {
  it('reports how much of a level was eaten', () => {
    const c = sideChanges([lv(100, 50), lv(99, 20)], [lv(100, 20), lv(99, 20)], 'bid')
    expect(c.hits.get(100)).toBeCloseTo(0.6)
    expect(c.hits.has(99)).toBe(false)
    expect(c.gone).toEqual([])
  })

  it('reports levels eaten through at the top of the book', () => {
    const c = sideChanges([lv(101, 30), lv(102, 40), lv(103, 10)], [lv(102, 10), lv(103, 10)], 'ask')
    expect(c.gone.map((l) => l.price)).toEqual([101])
    expect(c.hits.get(102)).toBeCloseTo(0.75)
  })

  it('treats a level leaving from deep in the book as a cancel, not a trade', () => {
    const c = sideChanges([lv(100, 10), lv(95, 10)], [lv(100, 10)], 'bid')
    expect(c.gone).toEqual([])
  })

  it('reports nothing on the first snapshot', () => {
    expect(sideChanges(undefined, [lv(100, 10)], 'bid').hits.size).toBe(0)
  })
})
