import * as THREE from 'three'
import type { Tier } from './quality'
import { useScene } from './useScene'

/**
 * The wet page behind the opening titles.
 *
 * Ink is pushed into damp paper: blots bloom out of the fold, spread along
 * the fibres, and settle into the grain. In the last half second the plates
 * shift — bands of the sheet slide and the colour separations pull apart —
 * and the whole thing dries out into the live site.
 *
 * All of it is generated: there is no image to download, which is why the
 * intro adds nothing to the page weight. The shape of every blot comes from
 * layered noise warped by more noise, which is the standard way to get
 * something organic out of arithmetic.
 */
export function IntroInk({ tier, seconds }: { tier: Tier; seconds: number }) {
  const uniforms = {
    uTime: { value: 0 },
    uProgress: { value: 0 },
    /* The intro is a dark press room, not a sheet of paper: the field is
       near-black and the ink blooms *lighter*, the way wet ink catches what
       little light there is. */
    uRoom: { value: new THREE.Color().setStyle('#0E0B0A', THREE.LinearSRGBColorSpace) },
    uBloom: { value: new THREE.Color().setStyle('#4A3327', THREE.LinearSRGBColorSpace) },
    uAspect: { value: 1 },
    uDetail: { value: tier === 'high' ? 5.0 : 3.0 },
  }

  const host = useScene({
    setup(stage) {
      stage.camera = new THREE.OrthographicCamera(-0.5, 0.5, 0.5, -0.5, 0, 1)
      stage.scene.add(new THREE.Mesh(
        new THREE.PlaneGeometry(1, 1),
        new THREE.ShaderMaterial({
          transparent: true,
          uniforms,
          vertexShader: `
            varying vec2 vUv;
            void main() { vUv = uv; gl_Position = vec4(position.xy * 2.0, 0.0, 1.0); }
          `,
          fragmentShader: `
            precision mediump float;
            varying vec2 vUv;
            uniform float uTime, uProgress, uAspect, uDetail;
            uniform vec3 uRoom, uBloom;

            /* Value noise: smooth random, the building block of everything below. */
            float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
            float noise(vec2 p) {
              vec2 i = floor(p), f = fract(p);
              f = f * f * (3.0 - 2.0 * f);
              return mix(mix(hash(i), hash(i + vec2(1,0)), f.x),
                         mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), f.x), f.y);
            }
            /* Fractal noise: the same noise at halving scales, which is what
               makes an edge look torn rather than drawn. */
            float fbm(vec2 p) {
              float v = 0.0, a = 0.5;
              for (int i = 0; i < 6; i++) {
                if (float(i) >= uDetail) break;
                v += a * noise(p);
                p *= 2.03; a *= 0.5;
              }
              return v;
            }

            void main() {
              vec2 uv = vUv;
              vec2 p = vec2(uv.x * uAspect, uv.y);

              /* Warping the lookup by more noise is what turns circular blots
                 into ink creeping along paper fibres. */
              vec2 warp = vec2(fbm(p * 3.0 + uTime * 0.05), fbm(p * 3.0 + 7.3 - uTime * 0.04));
              float field = fbm(p * 2.6 + warp * 0.9);

              /* The blot grows from the fold and stops short of the edges. */
              float fold = 1.0 - abs(uv.x - 0.5) * 1.5;
              float grow = smoothstep(0.0, 0.55, uProgress);
              float level = field * 0.75 + fold * 0.35;
              float blot = smoothstep(0.66 - grow * 0.30, 0.78 - grow * 0.26, level);

              /* Dry paper underneath: fibre grain, always there, stronger late. */
              /* A slow swell in the ink, so the room is never quite still. */
              float breathe = 0.85 + 0.15 * sin(uTime * 0.8);
              float grain = (hash(uv * 900.0 + uTime) - 0.5) * 0.012;

              vec3 colour = mix(uRoom, uBloom, blot * 0.55 * breathe) + grain;

              /* A vignette, so the eye goes to the sheet in the middle. */
              float edge = distance(uv, vec2(0.5)) * 1.3;
              colour *= 1.0 - edge * edge * 0.45;

              /* The last half second: the plates shift. Bands slide and the
                 separations pull apart before the sheet comes off the press. */
              float wipe = smoothstep(0.82, 1.0, uProgress);
              float band = step(0.5, fract(uv.y * 9.0 + uTime * 2.0));
              colour.r += wipe * band * 0.05;
              colour.b -= wipe * band * 0.04;
              colour = mix(colour, uBloom, wipe * 0.35);

              /* And it fades out entirely, leaving the real page behind it. */
              float alpha = 1.0 - smoothstep(0.88, 1.0, uProgress);
              gl_FragColor = vec4(colour, alpha);
            }
          `,
        }),
      ))
    },
    draw(stage, t) {
      uniforms.uTime.value = t
      uniforms.uProgress.value = Math.min(1, t / seconds)
      uniforms.uAspect.value = stage.width / Math.max(1, stage.height)
    },
    resize(stage) { uniforms.uAspect.value = stage.width / Math.max(1, stage.height) },
  }, tier, true)

  return <div className="dx-gl-intro" ref={host} aria-hidden="true" />
}
