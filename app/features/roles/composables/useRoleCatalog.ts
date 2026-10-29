import { useQuery } from '@tanstack/vue-query'
import {
  useWorkspaceContextValue,
  useWorkspaceServices,
} from '../../workspace/composables/useWorkspaceServices'
import { useEffectiveAccess } from '../../auth/composables/useEffectiveAccess'

const pageSize = 20

export function useRoleCatalog() {
  const services = useWorkspaceServices()
  const context = useWorkspaceContextValue()
  const access = useEffectiveAccess()
  const page = ref(0)
  const live = services.mode === 'live'
  const allowed = computed(() => access.data.value?.permissions.includes('roles.read') ?? false)
  const enabled = computed(() => live && !import.meta.server && allowed.value)
  const catalog = useQuery({
    queryKey: ['tenant-roles', context, page],
    queryFn: ({ signal }) => services.roles.list(page.value, pageSize, signal),
    enabled,
    placeholderData: (previous) => previous,
  })
  const previous = () => {
    if (page.value > 0) page.value -= 1
  }
  const next = () => {
    if (catalog.data.value && !catalog.data.value.last) page.value += 1
  }
  return {
    live,
    accessPending: access.isPending,
    allowed,
    catalog,
    page,
    pageSize,
    previous,
    next,
    refresh: () => catalog.refetch(),
  }
}
