import type { ReactNode } from 'react'
import './ui.css'

/**
 * The shared pieces every page is built from. They exist so that a figure, a
 * table or a chip looks the same wherever it appears, and so that no page has
 * to decide a size or a gap for itself.
 */

/** A headline figure with its label underneath. */
export function Stat({ value, label, size = 'lg', tone }:
  { value: ReactNode; label: string; size?: 'lg' | 'sm'; tone?: 'up' | 'down' | 'halt' }) {
  return (
    <div className={`dx-stat${size === 'sm' ? ' dx-stat-sm' : ''}`}>
      <span className={`dx-stat-value${tone ? ` is-${tone}` : ''}`}>{value}</span>
      <small className="dx-stat-label">{label}</small>
    </div>
  )
}

/** A small uppercase chip: a phase, a kind, a count. */
export function Tag({ children, tone }: { children: ReactNode; tone?: 'alert' | 'up' | 'down' }) {
  return <span className={`dx-tag${tone ? ` dx-tag-${tone}` : ''}`}>{children}</span>
}

/**
 * A panel of content. `quiet` drops the card treatment for places that sit
 * inside another card and only need the spacing.
 */
export function Card({ children, quiet, className, ...rest }:
  { children: ReactNode; quiet?: boolean; className?: string } & React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={`${quiet ? 'dx-card-quiet' : 'dx-card'}${className ? ` ${className}` : ''}`} {...rest}>
      {children}
    </div>
  )
}

/** The house table: names left, figures right, one rule under the head. */
export function DataTable({ head, children }: { head: string[]; children: ReactNode }) {
  return (
    <table className="dx-table dx-num">
      <thead><tr>{head.map((h) => <th key={h} scope="col">{h}</th>)}</tr></thead>
      <tbody>{children}</tbody>
    </table>
  )
}

/** What a panel says when it has nothing to show: plain ink, and what to do next. */
export function Empty({ children }: { children: ReactNode }) {
  return <p className="dx-empty">{children}</p>
}

/** A placeholder sheet while real content is still coming. */
export function Loading({ width = '100%', height = 16 }: { width?: number | string; height?: number | string }) {
  return <span className="dx-shimmer" style={{ width, height }} aria-hidden="true" />
}
