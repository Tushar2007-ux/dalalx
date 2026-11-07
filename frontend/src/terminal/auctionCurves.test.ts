import { describe, expect, it } from 'vitest'
import { auctionCurves, bestCross } from './auctionCurves'

const lv = (price: number, totalQty: number) => ({ price, totalQty, orderCount: 1 })

describe('auction curves', () => {
  // Bids: 100 @ 102, 50 @ 101. Asks: 80 @ 100, 60 @ 101.
  const points = auctionCurves([lv(102, 100), lv(101, 50)], [lv(100, 80), lv(101, 60)])

  it('demand falls and supply rises with price', () => {
    expect(points.map((p) => p.price)).toEqual([100, 101, 102])
    expect(points.map((p) => p.demand)).toEqual([150, 150, 100])
    expect(points.map((p) => p.supply)).toEqual([80, 140, 140])
  })

  it('picks the price that trades the most shares', () => {
    const best = bestCross(points)!
    expect(best.price).toBe(101)
    expect(Math.min(best.demand, best.supply)).toBe(140)
  })

  it('finds no cross when buyers and sellers do not overlap', () => {
    expect(bestCross(auctionCurves([lv(99, 10)], [lv(101, 10)]))).toBeNull()
  })
})
