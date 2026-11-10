import { describe, expect, it } from 'vitest'
import { flightPath } from './flight'

const box = (left: number, top: number) => ({ left, top, width: 100, height: 20 })

describe('flightPath', () => {
  it('starts at the centre of the level it leaves', () => {
    const f = flightPath(box(200, 400), box(600, 100))
    expect(f.x).toBe(250)
    expect(f.y).toBe(410)
  })

  it('travels the distance between the two centres', () => {
    const f = flightPath(box(200, 400), box(600, 100))
    expect(f.dx).toBe(400)
    expect(f.dy).toBe(-300)
  })

  it('takes longer the further it goes, within bounds', () => {
    const near = flightPath(box(0, 0), box(10, 0))
    const far = flightPath(box(0, 0), box(900, 600))
    expect(near.ms).toBe(320)          // a hop still takes the minimum
    expect(far.ms).toBe(760)           // and nothing takes longer than the maximum
    expect(flightPath(box(0, 0), box(400, 0)).ms).toBeGreaterThan(near.ms)
  })
})
