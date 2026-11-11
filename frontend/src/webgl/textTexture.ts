import * as THREE from 'three'

/**
 * Turns a line of type into a texture the GPU can push around.
 *
 * The paper's own display face is drawn into an ordinary 2D canvas and handed
 * to WebGL as a picture. That is deliberately simpler than the signed-distance
 * fields a dedicated text library would build: the figure here is one short
 * line at one size, so a plain bitmap at twice the resolution is sharp enough
 * and costs nothing to keep in step with the CSS around it.
 */
export interface TextPlate {
  texture: THREE.CanvasTexture
  /** Redraw with new words. Returns false when nothing changed. */
  write: (text: string) => boolean
  /** Resize the plate to its box. */
  fit: (width: number, height: number) => void
  dispose: () => void
}

export function makeTextPlate(font: string, colour: string, scale = 2, originX = 0): TextPlate {
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')!
  const texture = new THREE.CanvasTexture(canvas)
  texture.minFilter = THREE.LinearFilter
  texture.magFilter = THREE.LinearFilter
  let last = ''
  let box = { width: 1, height: 1 }

  const paint = () => {
    const { width, height } = box
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.save()
    ctx.scale(scale, scale)
    ctx.font = font
    ctx.fillStyle = colour
    ctx.textAlign = 'left'
    ctx.textBaseline = 'middle'
    ctx.fillText(last, originX, height / 2)
    ctx.restore()
    texture.needsUpdate = true
    void width
  }

  return {
    texture,
    write(text) {
      if (text === last) return false
      last = text
      paint()
      return true
    },
    fit(width, height) {
      box = { width: Math.max(1, width), height: Math.max(1, height) }
      canvas.width = Math.round(box.width * scale)
      canvas.height = Math.round(box.height * scale)
      paint()
    },
    dispose() { texture.dispose() },
  }
}
