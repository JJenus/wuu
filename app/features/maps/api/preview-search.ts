import type { MapSearchResult, MapSearchService } from '../domain/types'
const places: MapSearchResult[] = [
  {
    id: 'place-dogpatch',
    label: 'Dogpatch depot',
    description: 'San Francisco · Sample place',
    kind: 'place',
    source: 'preview',
    coordinate: { latitude: 37.755, longitude: -122.387 },
  },
  {
    id: 'place-mission',
    label: 'Mission District',
    description: 'San Francisco · Sample place',
    kind: 'place',
    source: 'preview',
    coordinate: { latitude: 37.759, longitude: -122.421 },
  },
  {
    id: 'place-soma',
    label: 'SOMA',
    description: 'San Francisco · Sample place',
    kind: 'place',
    source: 'preview',
    coordinate: { latitude: 37.778, longitude: -122.405 },
  },
  {
    id: 'place-richmond',
    label: 'Richmond District',
    description: 'San Francisco · Sample place',
    kind: 'place',
    source: 'preview',
    coordinate: { latitude: 37.78, longitude: -122.47 },
  },
  {
    id: 'place-financial',
    label: 'Financial District',
    description: 'San Francisco · Sample place',
    kind: 'place',
    source: 'preview',
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
    position: { lat: number; lng: number } | null
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
        coordinate: v.position
          ? { latitude: v.position.lat, longitude: v.position.lng }
          : undefined,
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
