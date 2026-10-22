export interface MapSearchResult {
  id: string
  label: string
  description: string
  kind: 'vehicle' | 'place'
  source: 'preview' | 'live'
  coordinate?: { latitude: number; longitude: number }
}
export interface MapSearchService {
  search(query: string, options: { signal: AbortSignal }): Promise<MapSearchResult[]>
}
export interface MapDataState {
  source: 'preview' | 'live'
  status: 'loading' | 'ready' | 'error'
  message?: string
}
