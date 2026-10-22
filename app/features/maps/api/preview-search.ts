import type { MapSearchResult, MapSearchService } from '../domain/types'
const places: MapSearchResult[] = [
  {
    id: 'place-dogpatch',
    label: 'Dogpatch depot',
    description: 'San Francisco · Sample place',
    kind: 'place',
    source: 'preview',
    canvasPosition: { x: 58, y: 84 },
    coordinate: { latitude: 37.755, longitude: -122.387 },
  },
  {
    id: 'place-mission',
    label: 'Mission District',
    description: 'San Francisco · Sample place',
    kind: 'place',
    source: 'preview',
    canvasPosition: { x: 33, y: 82 },
    coordinate: { latitude: 37.759, longitude: -122.421 },
  },
  {
    id: 'place-soma',
    label: 'SOMA',
    description: 'San Francisco · Sample place',
    kind: 'place',
    source: 'preview',
    canvasPosition: { x: 50, y: 61 },
    coordinate: { latitude: 37.778, longitude: -122.405 },
  },
  {
    id: 'place-richmond',
    label: 'Richmond District',
    description: 'San Francisco · Sample place',
    kind: 'place',
    source: 'preview',
    canvasPosition: { x: 19, y: 36 },
    coordinate: { latitude: 37.78, longitude: -122.47 },
  },
  {
    id: 'place-financial',
    label: 'Financial District',
    description: 'San Francisco · Sample place',
    kind: 'place',
    source: 'preview',
    canvasPosition: { x: 65, y: 41 },
    coordinate: { latitude: 37.794, longitude: -122.401 },
  },
]
export function createPreviewMapSearch(
  vehicles: () => readonly {
    id: string
    name: string
    plate: string
    driver: string
    location: string
    x: number
    y: number
  }[],
): MapSearchService {
  return {
    async search(query, { signal }) {
      signal.throwIfAborted()
      const vehicleResults: MapSearchResult[] = vehicles().map((v) => ({
        id: v.id,
        label: v.name,
        description: `${v.plate} · ${v.driver} · ${v.location}`,
        kind: 'vehicle',
        source: 'preview',
        canvasPosition: { x: v.x, y: v.y },
      }))
      const text = query.trim().toLowerCase().slice(0, 120)
      return structuredClone(
        [...vehicleResults, ...places]
          .filter((p) => `${p.label} ${p.description}`.toLowerCase().includes(text))
          .slice(0, 8),
      )
    },
  }
}
