import { describe, expect, it } from 'vitest'
import { bubbleLine } from './bubbles'

const t = (side: 'BUY' | 'SELL', qty = 200) => ({ at: 0, symbol: 'RELIANCE', side, qty })

describe('bot speech bubbles', () => {
  it('speak in each bot’s own voice', () => {
    expect(bubbleLine('WHALE', t('SELL', 5000))).toBe('Dumping 5,000 RELIANCE. Slice by slice.')
    expect(bubbleLine('PANIC', t('SELL'))).toContain('SELL!')
    expect(bubbleLine('MUNIM', t('BUY'))).toContain('Quoting again')
  })

  it('fall back to a plain line for hired bots', () => {
    expect(bubbleLine('CONTRA-1', t('BUY', 10))).toBe('Bought 10 RELIANCE')
  })
})
