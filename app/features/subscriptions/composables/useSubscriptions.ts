import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import {
  useWorkspaceContextValue,
  useWorkspaceServices,
} from '../../workspace/composables/useWorkspaceServices'
import { errorMessage } from '../../core/domain/errors'

export function useSubscriptions() {
  const services = useWorkspaceServices()
  const context = useWorkspaceContextValue()
  const cache = useQueryClient()
  const root = ['subscriptions', context] as const
  const enabled = services.mode === 'live' && !import.meta.server
  const catalog = useQuery({
    queryKey: [...root, 'catalog'],
    queryFn: ({ signal }) => services.subscriptions.catalog(signal),
    enabled,
  })
  const owned = useQuery({
    queryKey: [...root, 'owned'],
    queryFn: ({ signal }) => services.subscriptions.list(signal),
    enabled,
  })
  // Keep the operation key across ambiguous failures. It is released only after a confirmed receipt.
  const attempts = new Map<string, string>()
  const message = ref('')
  const failure = ref('')
  const enroll = useMutation({
    mutationFn: ({ planId, version }: { planId: string; version: number }) => {
      const plan = `${planId}:${version}`
      const key = attempts.get(plan) ?? crypto.randomUUID()
      attempts.set(plan, key)
      return services.subscriptions.enroll(planId, version, key)
    },
    onSuccess: async (_, plan) => {
      attempts.delete(`${plan.planId}:${plan.version}`)
      message.value = 'Enrollment recorded. This is not payment or active access.'
      await cache.invalidateQueries({ queryKey: [...root, 'owned'] })
    },
  })
  const cancel = useMutation({
    mutationFn: (id: string) => services.subscriptions.cancel(id),
    onSuccess: async () => {
      message.value = 'Enrollment canceled. No payment or refund was processed.'
      await cache.invalidateQueries({ queryKey: [...root, 'owned'] })
    },
  })
  async function perform(action: () => Promise<unknown>) {
    message.value = ''
    failure.value = ''
    try {
      await action()
    } catch (error) {
      failure.value = errorMessage(error)
    }
  }
  return {
    live: services.mode === 'live',
    catalog,
    owned,
    message,
    failure,
    busy: computed(() => enroll.isPending.value || cancel.isPending.value),
    enroll: (planId: string, version: number) =>
      perform(() => enroll.mutateAsync({ planId, version })),
    cancel: (id: string) => perform(() => cancel.mutateAsync(id)),
  }
}
