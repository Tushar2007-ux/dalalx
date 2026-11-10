import { Component, type ErrorInfo, type ReactNode } from 'react'

/**
 * Catches a crash anywhere in the page so the whole app doesn't go blank:
 * prints a "presses jammed" notice with a button to reload. (React only
 * offers this as a class component.)
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null }

  static getDerivedStateFromError(error: Error) {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('DalalX page crashed', error, info.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <main role="alert" style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: 'var(--paper)', color: 'var(--ink)', padding: 'var(--space-5)' }}>
        <div style={{ maxWidth: 520, display: 'grid', gap: 'var(--space-3)' }}>
          <span style={{ fontFamily: 'var(--font-ui)', fontSize: 'var(--text-label)', fontWeight: 700, letterSpacing: '0.14em', textTransform: 'uppercase', color: 'var(--accent)' }}>Stop press</span>
          <h1 style={{ margin: 0, fontFamily: 'var(--font-display)', fontSize: 'var(--text-figure)', lineHeight: 1.1 }}>The presses jammed</h1>
          <p style={{ margin: 0, fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 'var(--text-deck)' }}>
            Something on this page broke. The exchange itself is unaffected; your orders are safe.
          </p>
          <code style={{ fontSize: 'var(--text-small)', color: 'var(--ink-muted)' }}>{this.state.error.message}</code>
          <button type="button" onClick={() => location.reload()} style={{ justifySelf: 'start', padding: '8px 14px', border: '1px solid var(--rule-strong)', background: 'var(--ink)', color: 'var(--ink-inverse)', cursor: 'pointer' }}>
            Reprint the page
          </button>
        </div>
      </main>
    )
  }
}
