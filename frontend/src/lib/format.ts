/**
 * Formatting helpers. The server works in whole paise (integers) so money is
 * never rounded wrongly; we only divide by 100 at the very last moment, for display.
 */

export const PAISE_PER_RUPEE = 100

/** 248650 → 2486.5 (a number of rupees, for components that expect rupees). */
export function toRupees(paise: number): number {
  return paise / PAISE_PER_RUPEE
}

/**
 * "2,486.50" → 248650. Returns null for anything that is not a positive amount
 * with at most two decimals. Works on the text, not on floats, so "0.1 + 0.2"
 * style errors cannot creep in.
 */
export function parseRupees(text: string): number | null {
  const clean = text.replace(/[,\s₹]/g, '')
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(clean)
  if (!match) return null
  const paise = Number(match[1]) * PAISE_PER_RUPEE + Number((match[2] ?? '').padEnd(2, '0'))
  return paise > 0 ? paise : null
}

/** 248650 → "2,486.50" (Indian digit grouping). */
export function formatPaise(paise: number): string {
  return toRupees(paise).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 1234567 → "12,34,567". */
export function formatQty(qty: number): string {
  return qty.toLocaleString('en-IN')
}

const pad = (n: number, width = 2) => String(n).padStart(width, '0')

/** ISO timestamp → "14:39:52.120", the tape's clock format. */
export function clockTime(iso: string | number | Date): string {
  const d = new Date(iso)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`
}

/** Date → "14:39". */
export function hhmm(d: Date): string {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Percentage change from a reference price, e.g. 2.15 for +2.15%. */
export function changePct(price: number, reference: number): number {
  return reference ? ((price - reference) / reference) * 100 : 0
}
