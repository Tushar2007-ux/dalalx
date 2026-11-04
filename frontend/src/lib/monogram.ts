/** Two letters for a hired bot's stamp: "The Contrarian" → "CO". */
export function monogramOf(name: string): string {
  return (name.replace(/^The\s+/i, '').replace(/[^A-Za-z0-9]/g, '').slice(0, 2) || '??').toUpperCase()
}
