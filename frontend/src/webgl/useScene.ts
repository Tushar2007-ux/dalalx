import { useEffect, useRef } from 'react'
import * as THREE from 'three'
import type { Tier } from './quality'
import { pixelRatioFor } from './quality'

/** What a scene is handed when it is set up, and again on every frame. */
export interface Stage {
  renderer: THREE.WebGLRenderer
  scene: THREE.Scene
  /** A scene may swap this for an orthographic camera in `setup`. */
  camera: THREE.Camera
  width: number
  height: number
  tier: Tier
}

export interface SceneSpec {
  /** Build the scene once. Anything returned is passed back to every draw. */
  setup: (stage: Stage) => void
  /** One frame. `t` is seconds since the scene started, `dt` since last frame. */
  draw: (stage: Stage, t: number, dt: number) => void
  /** Optional: react to a resize beyond what the camera does by itself. */
  resize?: (stage: Stage) => void
}

/**
 * Runs one WebGL scene inside an element, and gets out of the way when it
 * should not be running.
 *
 * Every 3D piece in the paper shares this, so there is one place that knows
 * how to size a canvas to its box, cap the pixel ratio for the machine, stop
 * drawing when the tab is hidden or the canvas has scrolled out of view, and
 * — the part that is easy to forget — give every bit of GPU memory back when
 * the page changes. A leaked renderer is invisible until the browser runs out
 * of contexts and the whole demo goes blank.
 */
export function useScene(spec: SceneSpec, tier: Tier, enabled: boolean) {
  const host = useRef<HTMLDivElement>(null)
  const live = useRef(spec)
  live.current = spec

  useEffect(() => {
    const box = host.current
    if (!box || !enabled || tier === 'off') return

    let renderer: THREE.WebGLRenderer
    try {
      renderer = new THREE.WebGLRenderer({ antialias: tier === 'high', alpha: true, powerPreference: 'low-power' })
    } catch {
      return   // No context to be had: the flat version underneath stands on its own.
    }
    renderer.setPixelRatio(pixelRatioFor(tier, devicePixelRatio))
    /* These scenes write finished sRGB colours straight out of the shader —
       they are printing ink, not lighting a 3D object — so three's automatic
       linear-to-sRGB conversion is turned off. Left on, every colour read
       from a CSS variable comes out lighter and more saturated than the page
       around it, which on a paper-coloured site is immediately obvious. */
    renderer.outputColorSpace = THREE.LinearSRGBColorSpace
    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100)
    const stage: Stage = { renderer, scene, camera, width: 1, height: 1, tier }

    const size = () => {
      const { width, height } = box.getBoundingClientRect()
      stage.width = Math.max(1, Math.round(width))
      stage.height = Math.max(1, Math.round(height))
      renderer.setSize(stage.width, stage.height, false)
      if (stage.camera instanceof THREE.PerspectiveCamera) {
        stage.camera.aspect = stage.width / stage.height
        stage.camera.updateProjectionMatrix()
      }
      live.current.resize?.(stage)
    }

    box.appendChild(renderer.domElement)
    renderer.domElement.style.width = '100%'
    renderer.domElement.style.height = '100%'
    renderer.domElement.style.display = 'block'
    size()
    live.current.setup(stage)

    // Drawing a canvas nobody can see is pure waste, so both the tab being
    // hidden and the canvas being scrolled away stop the loop.
    let onScreen = true
    const watcher = new IntersectionObserver(([e]) => { onScreen = e.isIntersecting }, { threshold: 0 })
    watcher.observe(box)
    const resizer = new ResizeObserver(size)
    resizer.observe(box)

    let frame = 0
    const clock = new THREE.Clock()
    const tick = () => {
      frame = requestAnimationFrame(tick)
      const dt = Math.min(clock.getDelta(), 0.1)   // a backgrounded tab must not jump the animation
      if (!onScreen || document.hidden) return
      live.current.draw(stage, clock.getElapsedTime(), dt)
      renderer.render(scene, stage.camera)
    }
    tick()

    return () => {
      cancelAnimationFrame(frame)
      watcher.disconnect()
      resizer.disconnect()
      scene.traverse((o) => {
        const mesh = o as THREE.Mesh
        mesh.geometry?.dispose()
        const material = mesh.material as THREE.Material | THREE.Material[] | undefined
        if (Array.isArray(material)) material.forEach((m) => m.dispose())
        else material?.dispose()
      })
      renderer.dispose()
      renderer.domElement.remove()
    }
  }, [tier, enabled])

  return host
}
