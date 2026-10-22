import type { DataMode } from '../domain/data-mode'

export interface DataSourceState {
  mode: DataMode
  label: string
  isPreview: boolean
  isLive: boolean
}

export function useDataSource(): DataSourceState {
  const config = useRuntimeConfig()
  const mode = config.public.dataMode === 'live' ? 'live' : 'preview'
  return {
    mode,
    label: mode === 'live' ? 'Live mode' : 'Interactive preview',
    isPreview: mode === 'preview',
    isLive: mode === 'live',
  }
}
