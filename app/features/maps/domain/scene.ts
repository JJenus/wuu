import type { Coordinate } from './geographic'
import type { Geometry } from '../../geofences/domain/geofence'
import type { VehicleStatus } from '../../fleet/domain/fleet'
export interface GeographicMarker {
  id: string
  label: string
  status: VehicleStatus
  position: Coordinate | null
}
export interface MapScene {
  markers: GeographicMarker[]
  selectedId?: string | null
  follow?: boolean
  route?: Coordinate[]
  trail?: Coordinate[]
  searchPosition?: Coordinate | null
  showLabels?: boolean
  showRoute?: boolean
  geometry?: Geometry | null
  editable?: boolean
  tool?: 'pan' | 'circle' | 'polygon' | 'edit'
}
