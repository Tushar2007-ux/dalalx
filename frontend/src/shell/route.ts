import { useEffect, useState } from 'react'

/**
 * A minimal router: the page is chosen by the URL hash (#/correspondents).
 * Hash URLs need no server configuration and survive a refresh.
 */
export type Route = 'front' | 'floor' | 'xray' | 'correspondents' | 'surveillance' | 'demo'

/** The pages in the masthead's navigation, in reading order. */
export const ROUTES: { route: Route; hash: string; label: string }[] = [
  { route: 'front', hash: '#/', label: 'Front Page' },
  { route: 'floor', hash: '#/floor', label: 'Trading Floor' },
  { route: 'xray', hash: '#/xray', label: 'X-Ray' },
  { route: 'correspondents', hash: '#/correspondents', label: 'Correspondents' },
  { route: 'surveillance', hash: '#/surveillance', label: 'Investigations' },
]

/** #/demo starts the autopilot (it isn't in the navigation). Anything unknown shows the front page. */
export function routeOf(hash: string): Route {
  if (hash === '#/demo') return 'demo'
  return ROUTES.find((r) => r.hash === hash)?.route ?? 'front'
}

/** Moves to a page (used by the demo autopilot). */
export function go(route: Exclude<Route, 'demo'>): void {
  const target = ROUTES.find((r) => r.route === route)?.hash ?? '#/'
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
