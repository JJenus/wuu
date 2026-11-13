<script setup lang="ts">
import { teamCreateSchema, teamProfileSchema } from '#shared/team'
import type { TeamMember } from '../domain/team'
import { useTeam } from '../composables/useTeam'
const team = useTeam()
const { page, canRead, canWrite, canAssign, canRemove, busy, failure, notice } = team
const search = ref(''),
  formError = ref('')
const editor = ref<'create' | 'profile' | 'roles' | 'remove' | null>(null)
const target = ref<TeamMember | null>(null)
const draft = reactive({ email: '', firstName: '', lastName: '', password: '' })
const selected = ref<number[]>([])
const visible = computed(() =>
  (team.members.data.value?.items ?? []).filter((member) =>
    `${member.email} ${member.firstName} ${member.lastName} ${member.roles.join(' ')}`
      .toLowerCase()
      .includes(search.value.toLowerCase()),
  ),
)
const roleProblem = computed(() => {
  if (!target.value || !team.catalog.data.value) return 'Role catalog is not available.'
  return target.value.roles.some(
    (name) => team.catalog.data.value!.filter((role) => role.name === name).length !== 1,
  )
    ? 'Some assigned roles cannot be matched uniquely. Refresh the catalog before changing roles.'
    : ''
})
function open(mode: 'create' | 'profile' | 'roles' | 'remove', member?: TeamMember) {
  target.value = member ?? null
  editor.value = mode
  formError.value = ''
  failure.value = ''
  notice.value = ''
  Object.assign(draft, {
    email: member?.email ?? '',
    firstName: member?.firstName ?? '',
    lastName: member?.lastName ?? '',
    password: '',
  })
  selected.value = (team.catalog.data.value ?? [])
    .filter((role) => member?.roles.includes(role.name))
    .map((role) => role.id)
}
function close() {
  if (!busy.value) {
    editor.value = null
    target.value = null
    draft.password = ''
  }
}
async function save() {
  formError.value = ''
  if (editor.value === 'create' || editor.value === 'profile') {
    const profile = { email: draft.email, firstName: draft.firstName, lastName: draft.lastName }
    if (editor.value === 'create') {
      const input = teamCreateSchema.safeParse({ ...profile, password: draft.password })
      if (!input.success) {
        formError.value = input.error.issues[0]?.message ?? 'Check the account fields.'
        return
      }
      const result = await team.create(input.data)
      draft.password = ''
      if (result) close()
    } else if (target.value) {
      const input = teamProfileSchema.safeParse(profile)
      if (!input.success) {
        formError.value = input.error.issues[0]?.message ?? 'Check the profile fields.'
        return
      }
      if (await team.update(target.value, input.data)) close()
    }
  } else if (
    editor.value === 'roles' &&
    target.value &&
    !roleProblem.value &&
    !team.catalog.isError.value
  ) {
    if (!selected.value.length) {
      formError.value = 'Choose at least one role. Use Remove member to remove membership.'
      return
    }
    if (await team.assign(target.value, selected.value)) close()
  } else if (editor.value === 'remove' && target.value) {
    if (await team.remove(target.value)) close()
  }
}
onUnmounted(() => {
  draft.password = ''
})
</script>
<template>
  <div class="detail-stack">
    <div class="info-banner">
      <UIcon name="i-lucide-users" />
      <p>
        Live team membership · Permissions are enforced by the server. Account creation does not
        send invitation email. New accounts start as OPERATOR.
      </p>
    </div>
    <div class="toolbar">
      <UButton color="neutral" variant="outline" :disabled="busy" @click="team.refresh"
        >Refresh team</UButton
      >
      <UButton v-if="canWrite" icon="i-lucide-user-plus" :disabled="busy" @click="open('create')"
        >Create account</UButton
      >
    </div>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="failure" role="alert">{{ failure }}</p>
    <p v-if="team.access.isPending.value" role="status">Checking team access…</p>
    <p v-else-if="team.access.isError.value" role="alert">
      Team access could not be verified. Refresh to retry.
    </p>
    <p v-else-if="!canRead" role="status">You do not have permission to read the team directory.</p>
    <section
      v-if="editor"
      class="panel section-body detail-stack"
      :aria-label="
        editor === 'create'
          ? 'Create account'
          : editor === 'profile'
            ? 'Edit member'
            : editor === 'roles'
              ? 'Assign member roles'
              : 'Remove member'
      "
    >
      <h2>
        {{
          editor === 'create'
            ? 'Create account'
            : editor === 'profile'
              ? 'Edit member'
              : editor === 'roles'
                ? 'Assign member roles'
                : 'Remove member'
        }}
      </h2>
      <p v-if="target">{{ target.email }}</p>
      <form class="detail-stack" @submit.prevent="save">
        <fieldset
          :disabled="
            busy ||
            (editor === 'create' || editor === 'profile'
              ? !canWrite
              : editor === 'roles'
                ? !canAssign
                : !canRemove)
          "
          class="detail-stack"
        >
          <template v-if="editor === 'create' || editor === 'profile'">
            <label for="team-first">First name</label
            ><UInput id="team-first" v-model="draft.firstName" required maxlength="100" />
            <label for="team-last">Last name</label
            ><UInput id="team-last" v-model="draft.lastName" required maxlength="100" />
            <label for="team-email">Email</label
            ><UInput
              id="team-email"
              v-model="draft.email"
              type="email"
              required
              maxlength="254"
              autocomplete="off"
            />
            <template v-if="editor === 'create'"
              ><label for="team-password">Initial password</label
              ><UInput
                id="team-password"
                v-model="draft.password"
                type="password"
                required
                minlength="8"
                maxlength="72"
                autocomplete="new-password"
              />
              <p class="form-hint">
                8–72 characters. Share credentials securely outside this app; no invitation email is
                sent. Passwords are cleared after submission.
              </p></template
            >
            <p v-else class="form-hint">
              Email and profile changes may be rejected for identities shared across tenants.
              Password and organization are not changed here.
            </p>
          </template>
          <template v-else-if="editor === 'roles'">
            <p v-if="team.catalog.isError.value || roleProblem" role="alert">
              {{ roleProblem || 'Could not load the complete tenant role catalog.' }}
            </p>
            <label
              v-for="role in team.catalog.data.value ?? []"
              :key="role.id"
              class="flex items-center gap-2"
              ><input
                v-model="selected"
                type="checkbox"
                :value="role.id"
                :disabled="!!roleProblem || team.catalog.isError.value"
                :aria-label="`Assign ${role.name}`"
              /><span
                ><strong>{{ role.name }}</strong
                ><small class="block muted"
                  >{{ role.description }} · {{ role.permissions.join(', ') }}</small
                ></span
              ></label
            >
            <p class="form-hint">
              Replaces roles in this tenant only. The server prevents removing the last
              administrator.
            </p>
          </template>
          <p v-else>
            Confirm removing {{ target?.email }} from this tenant? This revokes their account
            sessions but does not delete their identity or memberships elsewhere. The last
            administrator cannot be removed.
          </p>
          <p v-if="formError" role="alert">{{ formError }}</p>
          <UButton
            type="submit"
            :loading="busy"
            :disabled="editor === 'roles' && (!!roleProblem || team.catalog.isError.value)"
            :color="editor === 'remove' ? 'error' : 'primary'"
            >{{
              editor === 'create'
                ? 'Create member account'
                : editor === 'profile'
                  ? 'Save profile'
                  : editor === 'roles'
                    ? 'Save roles'
                    : 'Confirm removal'
            }}</UButton
          >
        </fieldset>
        <UButton type="button" color="neutral" variant="outline" :disabled="busy" @click="close"
          >Cancel</UButton
        >
      </form>
    </section>
    <section v-if="canRead" class="panel" aria-label="Live team directory">
      <div class="toolbar">
        <UInput
          v-model="search"
          placeholder="Search this page"
          aria-label="Search this team page"
        /><span>{{ team.members.data.value?.total ?? 0 }} members</span>
      </div>
      <p v-if="team.members.isPending.value" class="section-body" role="status">Loading team…</p>
      <p v-else-if="team.members.isError.value" class="section-body" role="alert">
        {{ team.members.error.value?.message ?? 'Could not load team members.' }} No sample members
        are substituted.
      </p>
      <div v-else class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Member</th>
              <th>Tenant roles</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="member in visible" :key="member.id" :data-member-id="member.id">
              <td>
                <strong>{{ member.firstName }} {{ member.lastName }}</strong
                ><small class="block">{{ member.email }}</small>
              </td>
              <td>{{ member.roles.join(', ') || 'No tenant roles' }}</td>
              <td>{{ member.locked ? 'Locked' : member.enabled ? 'Enabled' : 'Disabled' }}</td>
              <td>
                <div class="flex flex-wrap gap-2">
                  <UButton
                    v-if="canWrite"
                    color="neutral"
                    variant="outline"
                    :disabled="busy"
                    :aria-label="`Edit ${member.email}`"
                    @click="open('profile', member)"
                    >Edit</UButton
                  >
                  <UButton
                    v-if="canAssign"
                    color="neutral"
                    variant="outline"
                    :disabled="busy || team.catalog.isPending.value || team.catalog.isError.value"
                    :aria-label="`Change roles for ${member.email}`"
                    @click="open('roles', member)"
                    >Change roles</UButton
                  >
                  <UButton
                    v-if="canRemove"
                    color="error"
                    variant="outline"
                    :disabled="busy"
                    :aria-label="`Remove ${member.email}`"
                    @click="open('remove', member)"
                    >Remove</UButton
                  >
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="!visible.length" class="section-body">No team members found on this page.</p>
      </div>
      <div class="toolbar">
        <UButton color="neutral" variant="outline" :disabled="page === 0 || busy" @click="page--"
          >Previous team page</UButton
        ><span>Page {{ page + 1 }}</span
        ><UButton
          color="neutral"
          variant="outline"
          :disabled="!team.members.data.value || team.members.data.value.last || busy"
          @click="page++"
          >Next team page</UButton
        >
      </div>
    </section>
  </div>
</template>
