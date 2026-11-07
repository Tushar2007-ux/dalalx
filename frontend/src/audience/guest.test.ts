import { beforeEach, describe, expect, it } from 'vitest'
import { joinUrl, loadGuest, rankMoves, saveGuest, signedRupees } from './guest'

describe('audience helpers', () => {
  beforeEach(() => localStorage.clear())

  it('swaps localhost for the Wi-Fi address and keeps the port', () => {
    expect(joinUrl({ protocol: 'http:', hostname: 'localhost', port: '5173' }, '192.168.1.7'))
      .toBe('http://192.168.1.7:5173/#/join')
    expect(joinUrl({ protocol: 'http:', hostname: '10.0.0.4', port: '5173' }, '192.168.1.7'))
      .toBe('http://10.0.0.4:5173/#/join')
    expect(joinUrl({ protocol: 'http:', hostname: 'localhost', port: '5173' }, null)).toBeNull()
  })

  it('remembers the guest on this phone', () => {
    expect(loadGuest()).toBeNull()
    saveGuest({ traderId: 'GUEST-1', name: 'Richa', token: 'abc' })
    expect(loadGuest()?.name).toBe('Richa')
    saveGuest(null)
    expect(loadGuest()).toBeNull()
  })

  it('ignores a corrupted saved guest', () => {
    localStorage.setItem('dalalx-guest', '{"nope":1}')
    expect(loadGuest()).toBeNull()
  })

  it('works out who climbed and who fell', () => {
    const s = (traderId: string, rank: number) => ({ traderId, rank, name: traderId, kind: 'BOT' as const, pnl: 0, trades: 0, volume: 0 })
    expect(rankMoves([s('A', 1), s('B', 2)], [s('B', 1), s('A', 2), s('C', 3)])).toEqual({ B: 1, A: -1, C: 0 })
  })

  it('prints signed rupees', () => {
    expect(signedRupees(125_000)).toBe('+₹1,250')
    expect(signedRupees(-8_000)).toBe('−₹80')
    expect(signedRupees(0)).toBe('₹0')
  })
})
