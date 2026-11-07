import { describe, expect, it } from 'vitest'
import { actionFor, isTyping } from './shortcuts'

const SYMBOLS = ['RELIANCE', 'TCS', 'INFY', 'HDFCBANK', 'TATAMOTORS']

describe('keyboard shortcuts', () => {
  it('B and S choose the ticket side, in either case', () => {
    expect(actionFor('b', SYMBOLS)).toEqual({ kind: 'side', side: 'buy' })
    expect(actionFor('S', SYMBOLS)).toEqual({ kind: 'side', side: 'sell' })
  })

  it('numbers pick a stock only if it exists', () => {
    expect(actionFor('3', SYMBOLS)).toEqual({ kind: 'select', symbol: 'INFY' })
    expect(actionFor('9', SYMBOLS)).toBeNull()
  })

  it('toggles modes and moves between pages', () => {
    expect(actionFor('v', SYMBOLS)).toEqual({ kind: 'toggle', what: 'viva' })
    expect(actionFor('p', SYMBOLS)).toEqual({ kind: 'toggle', what: 'presenter' })
    expect(actionFor('?', SYMBOLS)).toEqual({ kind: 'toggle', what: 'help' })
    expect(actionFor('e', SYMBOLS)).toEqual({ kind: 'go', hash: '#/engine' })
    expect(actionFor('Escape', SYMBOLS)).toEqual({ kind: 'escape' })
    expect(actionFor('x', SYMBOLS)).toBeNull()
  })

  it('never fires while typing in a field', () => {
    expect(isTyping(document.createElement('input'))).toBe(true)
    expect(isTyping(document.createElement('div'))).toBe(false)
    expect(isTyping(null)).toBe(false)
  })
})
