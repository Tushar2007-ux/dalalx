import { useEffect, useState } from 'react'

/**
 * A minimal router: the page is chosen by the URL hash (#/correspondents).
 * Hash URLs need no server configuration and survive a refresh.
 */
export type Route = 'floor' | 'xray' | 'correspondents' | 'surveillance'

export const ROUTES: { route: Route; hash: string; label: string }[] = [
  { route: 'floor', hash: '#/', label: 'Trading Floor' },
  { route: 'xray', hash: '#/xray', label: 'X-Ray' },
  { route: 'correspondents', hash: '#/correspondents', label: 'Correspondents' },
  { route: 'surveillance', hash: '#/surveillance', label: 'Investigations' },
]

export function routeOf(hash: string): Route {
  return ROUTES.find((r) => r.hash === hash && r.route !== 'floor')?.route ?? 'floor'
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
