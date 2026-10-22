import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { isRetryable } from '../../core/domain/errors'
import { queryKeys } from '../../core/queries/keys'
import type { Preferences } from '../domain/types'
import { useWorkspaceContextValue, useWorkspaceServices } from './useWorkspaceServices'

export function usePreferences() {
  const services = useWorkspaceServices()
  const context = useWorkspaceContextValue()
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: queryKeys.preferences.current(context),
    queryFn: ({ signal }) => services.preferences.get({ signal }),
    enabled: services.mode === 'preview' || import.meta.client,
    retry: (count, error) => count < 1 && isRetryable(error),
  })
  const mutation = useMutation({
    mutationFn: (input: Preferences) => services.preferences.save(input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.preferences.current(context) })
    },
  })
  return {
    preferences: query.data,
    ...query,
    savePreferences: mutation.mutateAsync,
    isSaving: mutation.isPending,
  }
}
