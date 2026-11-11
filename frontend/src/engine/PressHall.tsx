import { useRef } from 'react'
import * as THREE from 'three'
import type { WorkerLoad } from '../lib/types'
import { slipBudget, type Tier } from '../webgl/quality'
import { type Stage, useScene } from '../webgl/useScene'
import { binFill, laneX, shareBudget, slipRate, strokesPerSecond, trayHeight } from './hall'

/**
 * The print hall: the exchange's threads as machinery you can look at.
 *
 * One press per stock, standing in a row. Paper rides up each belt into that
 * press's bin — the worker's own BlockingQueue — and the press strokes to take
 * them off, one at a time. A press that is idle stands still; a press under
 * load hammers; a bin that is filling faster than its press can empty it rises.
 *
 * That is the whole concurrency model, drawn: five lanes that never touch each
 * other, each with exactly one machine working its own pile. Nothing here is
 * decorative — every moving part is driven by a number measured from the real
 * threads four times a second.
 *
 * Everything is built from boxes with flat ink colours and drawn outlines,
 * which is cheaper than lit, shaded 3D and looks like an engraving in a paper
 * rather than a videogame.
 */
/**
 * Puts the camera where the whole hall is in shot.
 *
 * The box this sits in is very wide and not tall, and how much of the scene
 * fits vertically depends on the distance, so the distance is worked out from
 * the box rather than fixed — otherwise the presses lose their heads on a
 * short screen. A slow drift keeps a still frame from looking frozen.
 */
function frame(camera: THREE.PerspectiveCamera, stage: Stage, t: number) {
  const aspect = stage.width / Math.max(1, stage.height)
  const needed = 3.6                                   // world units the hall is tall
  const vertical = needed / (2 * Math.tan((camera.fov * Math.PI) / 360))
  const horizontal = 11.5 / (2 * Math.tan((camera.fov * Math.PI) / 360) * aspect)
  const distance = Math.max(vertical, horizontal) + 1.6
  camera.position.set(Math.sin(t * 0.13) * 0.5, 2.3 + Math.sin(t * 0.19) * 0.08, distance)
  camera.lookAt(0, 0.85, -0.7)
}

export function PressHall({ workers, peak, tier }:
  { workers: WorkerLoad[]; peak: Record<string, number>; tier: Tier }) {
  // The scene is built once; the numbers change four times a second, so the
  // draw loop reads them from here instead of the scene being rebuilt.
  const live = useRef({ workers, peak })
  live.current = { workers, peak }

  const parts = useRef<{
    rams: THREE.Mesh[]
    fills: THREE.Mesh[]
    trays: THREE.Mesh[]
    slips: THREE.InstancedMesh | null
    slipState: { lane: number; t: number; speed: number; wob: number }[]
    phase: number[]
  }>({ rams: [], fills: [], trays: [], slips: null, slipState: [], phase: [] })

  const host = useScene({
    setup(stage: Stage) {
      const css = getComputedStyle(document.documentElement)
      const read = (name: string, fallback: string) =>
        new THREE.Color().setStyle(css.getPropertyValue(name).trim() || fallback, THREE.LinearSRGBColorSpace)
      const ink = read('--ink', '#1A1614')
      const paper = read('--paper', '#FBEAE0')
      const rule = read('--rule', '#C9B6AC')
      const up = read('--up', '#1F6B3C')
      const accent = read('--accent', '#9B2C2C')

      const count = Math.max(1, live.current.workers.length)
      const flat = (colour: THREE.Color) => new THREE.MeshBasicMaterial({ color: colour })
      const outline = new THREE.LineBasicMaterial({ color: ink, transparent: true, opacity: 0.55 })

      /** A box with its edges drawn, which is what gives the engraved look. */
      const block = (w: number, h: number, d: number, colour: THREE.Color) => {
        const geo = new THREE.BoxGeometry(w, h, d)
        const mesh = new THREE.Mesh(geo, flat(colour))
        mesh.add(new THREE.LineSegments(new THREE.EdgesGeometry(geo), outline))
        return mesh
      }

      const hall = new THREE.Group()
      parts.current.rams = []
      parts.current.fills = []
      parts.current.trays = []
      parts.current.phase = []

      for (let i = 0; i < count; i++) {
        const lane = new THREE.Group()
        lane.position.x = laneX(i, count, 2.05)

        // The belt runs away from the camera into the press.
        const belt = block(0.58, 0.06, 3.0, rule)
        belt.position.set(0, 0.26, 1.9)
        lane.add(belt)

        /* The bin is drawn as a wire cage rather than a solid, so what is
           inside it — the queue — is the thing you actually see. Five solid
           boxes in a row read as a wall and hide the machinery behind them. */
        const cage = new THREE.LineSegments(
          new THREE.EdgesGeometry(new THREE.BoxGeometry(0.76, 0.84, 0.64)), outline.clone(),
        )
        cage.position.set(0, 0.72, 0.5)
        lane.add(cage)

        const fill = block(0.7, 1, 0.58, accent)
        fill.position.set(0, 0.3, 0.5)
        fill.scale.y = 0.001
        fill.visible = false
        lane.add(fill)
        parts.current.fills.push(fill)

        // The press: two posts, a bed, and a ram that comes down on it.
        for (const side of [-0.52, 0.52]) {
          const post = block(0.11, 1.7, 0.11, ink)
          post.position.set(side, 0.85, -1.5)
          lane.add(post)
        }
        const crown = block(1.22, 0.15, 0.46, ink)
        crown.position.set(0, 1.75, -1.5)
        lane.add(crown)

        const bed = block(1.06, 0.13, 0.76, ink)
        bed.position.set(0, 0.065, -1.5)
        lane.add(bed)

        const ram = block(0.9, 0.3, 0.6, up)
        ram.position.set(0, 1.0, -1.5)
        lane.add(ram)
        parts.current.rams.push(ram)
        parts.current.phase.push(Math.random() * Math.PI * 2)

        // The tray: what the press has already printed, stacking up.
        /* The output stack stands beside the press, not behind it, or the
           press hides the one part that shows work actually finished. */
        const tray = block(0.62, 1, 0.5, paper)
        tray.position.set(0.72, 0.07, -2.1)
        tray.scale.y = 0.02
        lane.add(tray)
        parts.current.trays.push(tray)

        hall.add(lane)
      }

      // The floor, so the machines stand on something.
      const floor = new THREE.Mesh(
        new THREE.PlaneGeometry(count * 2.5, 10),
        new THREE.MeshBasicMaterial({ color: rule, transparent: true, opacity: 0.12 }),
      )
      floor.rotation.x = -Math.PI / 2
      floor.position.set(0, -0.01, 0.4)
      hall.add(floor)

      // All the paper in the hall is one instanced mesh: one draw call for
      // every slip on every belt, which is what keeps this cheap.
      const budget = slipBudget(stage.tier)
      const slips = new THREE.InstancedMesh(
        new THREE.PlaneGeometry(0.3, 0.22),
        new THREE.MeshBasicMaterial({ color: paper, side: THREE.DoubleSide }),
        Math.max(1, budget),
      )
      slips.instanceMatrix.setUsage(THREE.DynamicDrawUsage)
      slips.frustumCulled = false
      hall.add(slips)
      parts.current.slips = slips
      parts.current.slipState = Array.from({ length: budget }, () => ({
        lane: -1, t: 0, speed: 0, wob: Math.random() * 6.28,
      }))

      stage.scene.add(hall)

      /* A long lens from further back: a wide one at this distance bends the
         outer lanes outwards and the hall stops reading as a row. */
      const camera = stage.camera as THREE.PerspectiveCamera
      camera.fov = 30
      camera.updateProjectionMatrix()
      frame(camera, stage, 0)
    },

    draw(stage, t, dt) {
      const { workers: ws, peak: peaks } = live.current
      const { rams, fills, trays, slips, slipState, phase } = parts.current
      if (!ws.length || !slips) return

      // The hall breathes very slightly, so a still frame never looks frozen.
      frame(stage.camera as THREE.PerspectiveCamera, stage, t)

      for (let i = 0; i < rams.length && i < ws.length; i++) {
        const w = ws[i]

        // The ram: a fast fall, a slower lift, so it reads as hammering
        // rather than floating. A resting press sits up at the top.
        const rate = strokesPerSecond(w.perSecond)
        if (rate > 0) {
          phase[i] += dt * rate * Math.PI * 2
          const s = (Math.sin(phase[i]) + 1) / 2
          rams[i].position.y = 0.62 + Math.pow(s, 0.6) * 0.72
        } else {
          rams[i].position.y += (1.34 - rams[i].position.y) * Math.min(1, dt * 3)
        }

        // The bin rises and falls with the real queue.
        /* The same held peak the lanes below use: a rush that lasts a
           fraction of a second would otherwise never be caught by a reading
           taken four times a second. */
        const deep = Math.max(w.pending, peaks[w.symbol] ?? 0)
        const want = binFill(deep, w.capacity) * 0.8
        fills[i].scale.y += (want - fills[i].scale.y) * Math.min(1, dt * 4)
        fills[i].position.y = 0.31 + fills[i].scale.y / 2
        // An empty queue shows an empty cage, not a thin red sheet on the floor.
        fills[i].visible = fills[i].scale.y > 0.012

        // The tray grows with everything the press has done since opening.
        const tall = Math.max(0.02, trayHeight(w.processed))
        trays[i].scale.y += (tall - trays[i].scale.y) * Math.min(1, dt * 2)
        trays[i].position.y = trays[i].scale.y / 2
      }

      // Paper on the belts. Each slip belongs to a lane, rides from the far
      // end to the bin, and is then handed to whichever lane most deserves it.
      const rates = ws.map((w) => slipRate(w))
      const share = shareBudget(rates, slipState.length)
      const wanted: number[] = []
      for (let lane = 0; lane < share.length; lane++) {
        for (let n = 0; n < share[lane]; n++) wanted.push(lane)
      }

      const m = new THREE.Matrix4()
      const q = new THREE.Quaternion()
      const pos = new THREE.Vector3()
      const scale = new THREE.Vector3(1, 1, 1)
      let next = 0
      for (let s = 0; s < slipState.length; s++) {
        const slip = slipState[s]
        if (slip.lane < 0 || slip.t >= 1) {
          // Recycle into whichever lane is owed paper, or park it out of sight.
          const lane = next < wanted.length ? wanted[next++] : -1
          slip.lane = lane
          slip.t = lane < 0 ? 1 : Math.random() * 0.15
          slip.speed = lane < 0 ? 0 : 0.35 + Math.min(0.9, rates[lane] / 14) * 0.9
        }
        if (slip.lane < 0) {
          m.makeScale(0, 0, 0)
          slips.setMatrixAt(s, m)
          continue
        }
        slip.t += dt * slip.speed
        const x = laneX(slip.lane, ws.length, 2.05)
        const z = 3.3 - slip.t * 2.9
        // A little lift and flutter as it travels, and a tip into the bin.
        const drop = Math.max(0, slip.t - 0.88) * 2.6
        pos.set(x + Math.sin(t * 2 + slip.wob) * 0.05, 0.33 + Math.sin(slip.t * 3.1) * 0.03 - drop, z)
        q.setFromEuler(new THREE.Euler(-1.2 + drop * 1.6, Math.sin(t * 1.6 + slip.wob) * 0.25, 0))
        m.compose(pos, q, scale)
        slips.setMatrixAt(s, m)
      }
      slips.instanceMatrix.needsUpdate = true
    },
  }, tier, true)

  return <div className="dx-hall" ref={host} aria-hidden="true" />
}
