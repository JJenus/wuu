import type { Vehicle } from '../../fleet/domain/fleet'
import { createPreviewMapSearch } from '../api/preview-search'
import type { MapSearchService } from '../domain/types'
export function useMapSearchProvider(
  vehicles: MaybeRefOrGetter<Vehicle[]>,
  override?: MaybeRefOrGetter<MapSearchService | undefined>,
) {
  const preview = createPreviewMapSearch(() => toValue(vehicles))
  return computed(() => (override && toValue(override)) || preview)
}
