import { useEffect, useRef } from 'react'
import * as THREE from 'three'
import { makeTextPlate } from './textTexture'
import { type Stage, useScene } from './useScene'
import type { Tier } from './quality'

/**
 * The headline price, printed by the GPU instead of the browser.
 *
 * On a tick the three colour channels pull apart for a fraction of a second
 * and a few bands of the figure slip sideways, the way a misregistered press
 * prints a number before the plates settle. It is the same idea the big
 * WebGL sites use for their text effects, and for the same reason: the
 * figure changes several times a second, and doing this in HTML would make
 * the browser re-lay-out the whole row every time.
 *
 * The real text stays in the DOM underneath, invisible but readable by a
 * screen reader, so nothing is lost when this is switched off.
 */
export function PriceGlitch({ text, tone, tier, anchor }:
  { text: string; tone?: 'up' | 'down'; tier: Tier; anchor: React.RefObject<HTMLElement | null> }) {
  const plate = useRef<ReturnType<typeof makeTextPlate> | null>(null)
  const uniforms = useRef({
    uMap: { value: null as THREE.Texture | null },
    uHit: { value: 0 },            // 1 at the moment of a tick, decaying to 0
    uTone: { value: 0 },           // +1 up, −1 down
    uTime: { value: 0 },
    uAspect: { value: 1 },
  })

  /* The figure must land exactly where the DOM one would, or switching 3D
     off would move the price. So the face, the size and the left edge are
     all read from the real element rather than guessed from the box. */
  const makePlate = (stage: Stage) => {
    const el = anchor.current
    /* Measured against the canvas itself, not the text's parent: the canvas
       is deliberately a little larger than the box so a slip has somewhere
       to go, and the figure would sit left of where it belongs otherwise. */
    const box = host.current?.getBoundingClientRect()
    const rect = el?.getBoundingClientRect()
    const css = el ? getComputedStyle(el) : null
    const face = css ? `${css.fontWeight} ${css.fontSize} ${css.fontFamily}` : '800 48px serif'
    const ink = css?.color || getComputedStyle(document.documentElement).getPropertyValue('--ink').trim() || '#1A1614'
    const originX = rect && box ? rect.left - box.left : 0
    uniforms.current.uAspect.value = stage.width / Math.max(1, stage.height)
    plate.current?.dispose()
    plate.current = makeTextPlate(face, ink, stage.tier === 'high' ? 2 : 1.25, originX)
    plate.current.fit(stage.width, stage.height)
    plate.current.write(text)
    uniforms.current.uMap.value = plate.current.texture
  }

  const host = useScene({
    setup(stage) {
      stage.camera = new THREE.OrthographicCamera(-0.5, 0.5, 0.5, -0.5, 0, 1)

      makePlate(stage)

      const quad = new THREE.Mesh(
        new THREE.PlaneGeometry(1, 1),
        new THREE.ShaderMaterial({
          transparent: true,
          uniforms: uniforms.current,
          vertexShader: `
            varying vec2 vUv;
            void main() { vUv = uv; gl_Position = vec4(position.xy * 2.0, 0.0, 1.0); }
          `,
          fragmentShader: `
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uMap;
            uniform float uHit, uTone, uTime, uAspect;

            float hash(float n) { return fract(sin(n) * 43758.5453123); }

            void main() {
              vec2 uv = vUv;

              /* A few horizontal bands slip sideways while the hit decays —
                 the plates of a press shifting before they settle. */
              float band = floor(uv.y * 14.0);
              float slip = (hash(band + floor(uTime * 24.0)) - 0.5) * 0.06 * uHit;
              uv.x += slip;

              /* Chromatic aberration: the channels pull apart, further on a
                 bigger move, and in the direction the price went. */
              float split = (0.004 + 0.016 * uHit) / uAspect;
              float dir = uTone == 0.0 ? 1.0 : uTone;
              float r = texture2D(uMap, uv + vec2( split * dir, 0.0)).a;
              float g = texture2D(uMap, uv).a;
              float b = texture2D(uMap, uv - vec2( split * dir, 0.0)).a;

              /* Ink is one colour; the split shows as colour only where the
                 channels disagree, which is exactly the edges of the glyphs. */
              vec3 ink = vec3(0.102, 0.086, 0.078);
              vec3 up  = vec3(0.04, 0.42, 0.22);
              vec3 dn  = vec3(0.70, 0.12, 0.12);
              vec3 edge = uTone > 0.0 ? up : dn;

              float a = max(g, max(r, b));
              float fringe = clamp(abs(r - b), 0.0, 1.0);
              vec3 colour = mix(ink, edge, fringe * (0.35 + 0.65 * uHit));

              /* The faintest grain, so the figure sits on paper rather than glass. */
              float grain = (hash(uv.x * 540.0 + uv.y * 311.0) - 0.5) * 0.05;
              gl_FragColor = vec4(colour + grain, a);
            }
          `,
        }),
      )
      stage.scene.add(quad)
    },
    draw(_stage, t, dt) {
      uniforms.current.uTime.value = t
      uniforms.current.uHit.value = Math.max(0, uniforms.current.uHit.value - dt * 3.2)
    },
    resize(stage) { makePlate(stage) },
  }, tier, true)

  // A new figure repaints the plate and kicks the hit back up to full.
  useEffect(() => {
    if (plate.current?.write(text)) uniforms.current.uHit.value = 1
    uniforms.current.uTone.value = tone === 'up' ? 1 : tone === 'down' ? -1 : 0
  }, [text, tone])

  useEffect(() => () => plate.current?.dispose(), [])

  return <div className="dx-gl-price" ref={host} aria-hidden="true" />
}
