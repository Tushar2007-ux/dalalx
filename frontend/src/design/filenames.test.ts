import { readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * A guard on the file names themselves.
 *
 * macOS and Windows treat `BigBoard.ts` and `bigBoard.ts` as the same file;
 * Linux does not. A pair like that builds perfectly on the machine it was
 * written on and fails `tsc` on the author's laptop with a confusing message
 * about a file "differing only in casing" — which is exactly what happened
 * here, and was invisible from the container the code was written in.
 */
function folders(dir: string): string[] {
  return [dir, ...readdirSync(dir)
    .map((n) => join(dir, n))
    .filter((p) => statSync(p).isDirectory())
    .flatMap(folders)]
}

const src = join(import.meta.dirname, '..')

describe('file names', () => {
  it.each(folders(src).map((d) => [d.split('/src')[1] || '/', d]))(
    'in src%s differ by more than their capitals', (_label, dir) => {
      const seen = new Map<string, string>()
      const clashes: string[] = []
      for (const name of readdirSync(dir)) {
        const key = name.toLowerCase()
        const first = seen.get(key)
        if (first) clashes.push(`${first} vs ${name}`)
        else seen.set(key, name)
      }
      expect(clashes).toEqual([])
    })
})
