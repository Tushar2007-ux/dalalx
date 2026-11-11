import { lazy } from 'react'

/**
 * The 3D pieces, loaded only if they are going to be used.
 *
 * three.js is about two thirds of a megabyte — more than the rest of DalalX
 * put together — and a reader on a weak machine, or one who has switched 3D
 * off, should never pay for it. Importing these through `lazy` puts the whole
 * library in a separate chunk that the browser only fetches when a tier above
 * `off` actually asks for a scene.
 *
 * Each is used inside a `<Suspense fallback={null}>`: the flat version is
 * already on the page, so there is nothing to show while the chunk arrives.
 */
export const PriceGlitch = lazy(() => import('./PriceGlitch').then((m) => ({ default: m.PriceGlitch })))
export const IntroInk = lazy(() => import('./IntroInk').then((m) => ({ default: m.IntroInk })))
export const PressHall = lazy(() => import('../engine/PressHall').then((m) => ({ default: m.PressHall })))
