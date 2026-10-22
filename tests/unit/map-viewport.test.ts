import { describe, expect, it } from 'vitest'
import {
  zoomCamera,
  panCamera,
  fitCamera,
  screenToWorld,
  clampCamera,
  zoomViewBox,
} from '../../app/features/maps/domain/viewport'
import { createPreviewMapSearch } from '../../app/features/maps/api/preview-search'
const home = { x: 50, y: 50, zoom: 1 }
describe('bounded map camera', () => {
  it('pans at the current zoom and clamps impossible camera values', () => {
    expect(panCamera(home, 10, -20)).toEqual({ x: 40, y: 70, zoom: 1 })
    expect(panCamera({ ...home, zoom: 2 }, 10, 20)).toEqual({ x: 45, y: 40, zoom: 2 })
    expect(clampCamera({ x: Infinity, y: NaN, zoom: 0 })).toEqual({ x: 50, y: 50, zoom: 0.65 })
    expect(clampCamera({ x: -999, y: 999, zoom: 100 })).toEqual({ x: -35, y: 135, zoom: 6 })
  })
  it('keeps the point under the cursor fixed when zooming', () => {
    const anchor = { x: 80, y: 30 }
    const before = screenToWorld(home, anchor)
    const after = zoomCamera(home, 2, anchor)
    expect(screenToWorld(after, anchor)).toEqual(before)
    expect(zoomCamera(home, NaN)).toEqual(home)
    expect(zoomCamera(home, -1)).toEqual(home)
  })
  it('fits all results with padding, handles empty/single points and invalid points', () => {
    expect(fitCamera([])).toEqual(home)
    expect(fitCamera([{ x: NaN, y: 1 }])).toEqual(home)
    expect(fitCamera([{ x: 40, y: 60 }])).toEqual({ x: 40, y: 60, zoom: 3 })
    const view = fitCamera([
      { x: 10, y: 20 },
      { x: 90, y: 80 },
    ])
    expect(view.x).toBe(50)
    expect(view.y).toBe(50)
    expect(view.zoom).toBeLessThan(1.1)
  })
  it('zooms an SVG viewbox around a chosen world point', () => {
    const view = { x: 0, y: 0, w: 900, h: 820 }
    expect(zoomViewBox(view, 0.5, { x: 450, y: 410 })).toEqual({ x: 225, y: 205, w: 450, h: 410 })
    expect(zoomViewBox(view, 2, { x: 0, y: 0 })).toEqual({ x: 0, y: 0, w: 1800, h: 1640 })
  })
})
describe('replaceable preview search contract', () => {
  it('searches vehicle name/registration/driver/place without network or mutating inputs', async () => {
    const vehicles = [
      {
        id: 'VH-1',
        name: 'Transit One',
        plate: 'CA123',
        driver: 'Maya',
        location: 'Market St',
        x: 46,
        y: 51,
      },
    ]
    const service = createPreviewMapSearch(() => vehicles)
    for (const query of ['transit', 'CA123', 'Maya', 'Market St'])
      expect(
        (await service.search(query, { signal: new AbortController().signal })).some(
          (r) => r.id === 'VH-1',
        ),
      ).toBe(true)
    const result = await service.search('Dogpatch', { signal: new AbortController().signal })
    expect(result[0]).toMatchObject({ kind: 'place', source: 'preview' })
    expect(result[0]?.coordinate).toMatchObject({ latitude: 37.755 })
    result[0]!.label = 'Changed'
    expect(
      (await service.search('Dogpatch', { signal: new AbortController().signal }))[0]?.label,
    ).not.toBe('Changed')
    expect(await service.search('unfindable', { signal: new AbortController().signal })).toEqual([])
    expect(
      (await service.search('', { signal: new AbortController().signal })).length,
    ).toBeLessThanOrEqual(8)
    const abort = new AbortController()
    abort.abort()
    await expect(service.search('Transit', { signal: abort.signal })).rejects.toThrow()
  })
})
