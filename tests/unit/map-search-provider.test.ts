import { describe, expect, it } from 'vitest'
import { createPreviewMapSearch } from '../../app/features/maps/api/preview-search'
describe('replaceable preview search contract', () => {
  it('keeps missing positions missing instead of inventing coordinates', async () => {
    const service = createPreviewMapSearch(() => [
      {
        id: 'new',
        name: 'New vehicle',
        plate: 'NEW',
        driver: 'Unassigned',
        location: 'No telemetry',
        position: null,
      },
    ])
    const results = await service.search('New vehicle', { signal: new AbortController().signal })
    expect(results[0]?.coordinate).toBeUndefined()
  })
  it('searches vehicle name/registration/driver/place without network or mutating inputs', async () => {
    const vehicles = [
      {
        id: 'VH-1',
        name: 'Transit One',
        plate: 'CA123',
        driver: 'Maya',
        location: 'Market St',
        position: { lat: 37.77, lng: -122.4 },
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
