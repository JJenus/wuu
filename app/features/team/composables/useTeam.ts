import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { useEffectiveAccess } from '../../auth/composables/useEffectiveAccess'
import {
  useWorkspaceContextValue,
  useWorkspaceServices,
} from '../../workspace/composables/useWorkspaceServices'
import { errorMessage, isApiError } from '../../core/domain/errors'
import type { TeamMember } from '../domain/team'
import type { TeamCreate, TeamProfile } from '#shared/team'
import type { Role } from '../../roles/api/role-service'

export function useTeam() {
  const services = useWorkspaceServices(),
    context = useWorkspaceContextValue(),
    client = useQueryClient()
  const access = useEffectiveAccess()
  const page = ref(0),
    busy = ref(false),
    failure = ref(''),
    notice = ref('')
  const permitted = (permission: string) =>
    !access.isPending.value &&
    !access.isError.value &&
    !!access.data.value?.permissions.includes(permission)
  const canRead = computed(() => permitted('users.read'))
  const canWrite = computed(() => permitted('users.write'))
  const canAssign = computed(() => permitted('users.assign_roles') && permitted('roles.read'))
  const canRemove = computed(() => permitted('users.delete'))
  const members = useQuery({
    queryKey: computed(() => ['team', context, 'list', page.value]),
    queryFn: ({ signal }) => services.team.list(page.value, signal),
    enabled: computed(() => services.mode === 'live' && !import.meta.server && canRead.value),
    retry: false,
  })
  const catalog = useQuery({
    queryKey: ['team', context, 'roles'],
    queryFn: async ({ signal }) => {
      const roles: Role[] = []
      for (let page = 0; page < 50; page++) {
        const result = await services.roles.list(page, 100, signal)
        roles.push(...result.items)
        if (result.last) return roles
      }
      throw new Error('Role catalog is incomplete; assignments are disabled.')
    },
    enabled: computed(() => !import.meta.server && canAssign.value),
    retry: false,
  })
  async function submit<T>(
    permission: string,
    operation: () => Promise<T>,
  ): Promise<{ value: T } | null> {
    if (busy.value) return null
    busy.value = true
    failure.value = ''
    notice.value = ''
    try {
      const current = await access.refetch()
      if (current.isError || !current.data?.permissions.includes(permission))
        throw new Error('You do not currently have permission for this team action.')
      const value = await operation()
      notice.value = 'Team change saved.'
      await client.invalidateQueries({ queryKey: ['team', context] })
      await access.refetch()
      return { value }
    } catch (error) {
      const uncertain =
        isApiError(error) && ['network', 'timeout', 'server', 'contract'].includes(error.kind)
      failure.value =
        errorMessage(error, 'The team change could not be completed.') +
        (uncertain
          ? ' It may have been saved. Refresh the team before retrying; requests are not automatically replayed.'
          : '')
      return null
    } finally {
      busy.value = false
    }
  }
  return {
    page,
    access,
    members,
    catalog,
    canRead,
    canWrite,
    canAssign,
    canRemove,
    busy,
    failure,
    notice,
    create: (input: TeamCreate) => submit('users.write', () => services.team.create(input)),
    update: (member: TeamMember, input: TeamProfile) =>
      submit('users.write', () => services.team.update(member.id, input)),
    assign: (member: TeamMember, roleIds: number[]) =>
      submit('users.assign_roles', () => services.team.assignRoles(member.id, roleIds)),
    remove: (member: TeamMember) => submit('users.delete', () => services.team.remove(member.id)),
    async refresh() {
      await access.refetch()
      await members.refetch()
      await catalog.refetch()
    },
  }
}
