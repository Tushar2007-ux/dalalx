import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * A guard on the stylesheets themselves.
 *
 * When the design system was introduced, a pass over the CSS replaced raw
 * numbers with tokens — and in seven places it chopped a delay in half,
 * leaving `3var(--dur-slide)`. That is not a CSS value, so the browser threw
 * away the whole declaration and the opening animation silently stopped
 * running. Nothing failed; it just stopped. This catches that shape of
 * mistake, which is invisible in a screenshot taken a second too late.
 */
function cssFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return cssFiles(path)
    return path.endsWith('.css') ? [path] : []
  })
}

const files = cssFiles(join(import.meta.dirname, '..'))

describe('the stylesheets', () => {
  it('are actually there to check', () => {
    expect(files.length).toBeGreaterThan(5)
  })

  it.each(files.map((f) => [f.split('/src/')[1], f]))('%s has no number glued to a var()', (_name, path) => {
    const offenders = readFileSync(path, 'utf8')
      .split('\n')
      .map((line, i) => [i + 1, line] as const)
      .filter(([, line]) => /\dvar\(/.test(line))
    expect(offenders).toEqual([])
  })

  it.each(files.map((f) => [f.split('/src/')[1], f]))('%s closes every block it opens', (_name, path) => {
    const css = readFileSync(path, 'utf8')
    expect(css.split('{').length).toBe(css.split('}').length)
  })
})
