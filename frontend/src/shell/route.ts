import { useEffect, useState } from 'react'

/**
 * A minimal router: the page is chosen by the URL hash (#/correspondents).
 * Hash URLs need no server configuration and survive a refresh.
 */
export type Route =
  | 'front' | 'floor' | 'xray' | 'correspondents' | 'surveillance' | 'engine' | 'audience' | 'how'
  | 'wall' | 'join' | 'demo'

/** Pages with the newspaper around them (everything except the big board, the phone page and the demo). */
export type ShellRoute = Exclude<Route, 'demo' | 'wall' | 'join'>

/** The pages in the section bar, in reading order. */
export const ROUTES: { route: ShellRoute; hash: string; label: string }[] = [
  { route: 'front', hash: '#/', label: 'Front Page' },
  { route: 'floor', hash: '#/floor', label: 'Trading Floor' },
  { route: 'xray', hash: '#/xray', label: 'X-Ray' },
  { route: 'correspondents', hash: '#/correspondents', label: 'Correspondents' },
  { route: 'surveillance', hash: '#/surveillance', label: 'Investigations' },
  { route: 'engine', hash: '#/engine', label: 'Engine Room' },
  { route: 'audience', hash: '#/audience', label: 'Audience' },
  { route: 'how', hash: '#/how', label: 'How It Works' },
]

/** Pages reached by link only: the full-screen big board, the phone page and the demo autopilot. */
const EXTRA: Record<string, Route> = { '#/wall': 'wall', '#/join': 'join', '#/demo': 'demo' }

/** Anything unknown shows the front page. */
export function routeOf(hash: string): Route {
  return EXTRA[hash] ?? ROUTES.find((r) => r.hash === hash)?.route ?? 'front'
}

/** Moves to a page (used by the demo autopilot). */
export function go(route: Exclude<Route, 'demo'>): void {
  const target = ROUTES.find((r) => r.route === route)?.hash
    ?? Object.keys(EXTRA).find((h) => EXTRA[h] === route) ?? '#/'
  if (location.hash !== target) location.hash = target
}

export function useRoute(): Route {
  const [route, setRoute] = useState<Route>(() => routeOf(location.hash))
  useEffect(() => {
    const onChange = () => {
      setRoute(routeOf(location.hash))
      window.scrollTo(0, 0)
    }
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return route
}
