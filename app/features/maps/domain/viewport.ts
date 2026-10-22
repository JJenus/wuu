export interface WorldPoint {
  x: number
  y: number
}
export interface MapCamera extends WorldPoint {
  zoom: number
}
const finite = (value: number, fallback: number) => (Number.isFinite(value) ? value : fallback)
export function clampCamera(camera: MapCamera): MapCamera {
  return {
    x: Math.max(-35, Math.min(135, finite(camera.x, 50))),
    y: Math.max(-35, Math.min(135, finite(camera.y, 50))),
    zoom: Math.max(0.65, Math.min(6, finite(camera.zoom, 1))),
  }
}
export function screenToWorld(camera: MapCamera, point: WorldPoint): WorldPoint {
  return { x: camera.x + (point.x - 50) / camera.zoom, y: camera.y + (point.y - 50) / camera.zoom }
}
export function panCamera(camera: MapCamera, dx: number, dy: number): MapCamera {
  return clampCamera({
    ...camera,
    x: camera.x - finite(dx, 0) / camera.zoom,
    y: camera.y - finite(dy, 0) / camera.zoom,
  })
}
export function zoomCamera(
  camera: MapCamera,
  factor: number,
  anchor: WorldPoint = { x: 50, y: 50 },
): MapCamera {
  if (!Number.isFinite(factor) || factor <= 0) return { ...camera }
  const point = screenToWorld(camera, anchor)
  const zoom = clampCamera({ ...camera, zoom: camera.zoom * factor }).zoom
  return clampCamera({
    x: point.x - (anchor.x - 50) / zoom,
    y: point.y - (anchor.y - 50) / zoom,
    zoom,
  })
}
export function fitCamera(points: readonly WorldPoint[]): MapCamera {
  const valid = points.filter((p) => Number.isFinite(p.x) && Number.isFinite(p.y))
  if (!valid.length) return { x: 50, y: 50, zoom: 1 }
  const minX = Math.min(...valid.map((p) => p.x)),
    maxX = Math.max(...valid.map((p) => p.x)),
    minY = Math.min(...valid.map((p) => p.y)),
    maxY = Math.max(...valid.map((p) => p.y))
  return clampCamera({
    x: (minX + maxX) / 2,
    y: (minY + maxY) / 2,
    zoom: Math.min(3, 80 / Math.max(maxX - minX, maxY - minY, 1)),
  })
}
export function zoomViewBox(
  view: { x: number; y: number; w: number; h: number },
  factor: number,
  anchor: WorldPoint = { x: view.x + view.w / 2, y: view.y + view.h / 2 },
) {
  const w = Math.max(180, Math.min(1800, view.w * factor)),
    ratio = w / view.w
  return {
    x: anchor.x - (anchor.x - view.x) * ratio,
    y: anchor.y - (anchor.y - view.y) * ratio,
    w,
    h: view.h * ratio,
  }
}
