import { describe, expect, it } from 'vitest'
import { pnlStat } from './BotsSection'

describe('pnlStat', () => {
  it('signs and colours profit and loss', () => {
    expect(pnlStat(421000)).toEqual({ label: 'P&L', value: '+₹4,210.00', dir: 'up' })
    expect(pnlStat(-61000)).toEqual({ label: 'P&L', value: '−₹610.00', dir: 'down' })
    expect(pnlStat(0).dir).toBeUndefined()
  })
})
