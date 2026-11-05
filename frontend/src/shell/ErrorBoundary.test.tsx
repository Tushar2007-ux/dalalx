import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { ErrorBoundary } from './ErrorBoundary'

function Broken(): never {
  throw new Error('kaput')
}

afterEach(cleanup)

describe('ErrorBoundary', () => {
  it('shows the jammed-presses notice instead of a blank page', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    render(<ErrorBoundary><Broken /></ErrorBoundary>)
    expect(screen.getByRole('alert').textContent).toContain('The presses jammed')
    expect(screen.getByText('kaput')).toBeTruthy()
  })
})
