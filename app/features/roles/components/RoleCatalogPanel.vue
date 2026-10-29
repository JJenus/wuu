<script setup lang="ts">
import { useRoleCatalog } from '../composables/useRoleCatalog'

const { live, accessPending, allowed, catalog, page, pageSize, previous, next, refresh } =
  useRoleCatalog()
const data = computed(() => catalog.data.value)
const pending = computed(() => accessPending.value || (allowed.value && !data.value))
const start = computed(() => (data.value?.total ? page.value * pageSize + 1 : 0))
const end = computed(() => Math.min((page.value + 1) * pageSize, data.value?.total ?? 0))
</script>

<template>
  <section
    v-if="live && (accessPending || allowed)"
    class="panel p-6 mb-6"
    aria-labelledby="tenant-roles-heading"
  >
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h2 id="tenant-roles-heading" class="text-xl font-semibold text-highlighted">
          Tenant roles
        </h2>
        <p class="muted mt-1">
          Read-only definitions from the active organization. Viewing a role does not grant its
          permissions.
        </p>
      </div>
      <UButton
        color="neutral"
        variant="outline"
        icon="i-lucide-refresh-cw"
        :loading="catalog.isFetching.value"
        @click="refresh"
        >Refresh roles</UButton
      >
    </div>
    <UiAsyncState
      :pending="pending"
      :error="allowed && catalog.isError.value ? catalog.error.value : null"
      loading-label="Loading tenant roles…"
      error-title="Tenant roles are unavailable"
      @retry="refresh"
    >
      <template v-if="data">
        <p v-if="!data.items.length" class="muted mt-4">
          No role definitions were returned for this organization.
        </p>
        <ul v-else class="mt-4 grid gap-3">
          <li
            v-for="role in data.items"
            :key="role.id"
            class="rounded-lg border border-default p-4"
          >
            <div class="flex flex-wrap items-baseline justify-between gap-2">
              <h3 class="font-mono text-sm font-semibold text-highlighted">{{ role.name }}</h3>
              <span class="muted text-xs"
                >{{ role.permissions.length }}
                {{ role.permissions.length === 1 ? 'permission' : 'permissions' }}</span
              >
            </div>
            <p class="muted mt-1 text-sm">
              {{ role.description || 'No description provided.' }}
            </p>
            <div v-if="role.permissions.length" class="mt-3 flex flex-wrap gap-1.5">
              <code
                v-for="permission in role.permissions"
                :key="permission"
                class="rounded bg-elevated px-2 py-0.5 text-xs"
                >{{ permission }}</code
              >
            </div>
            <p v-else class="muted mt-2 text-xs">No permissions assigned.</p>
          </li>
        </ul>
        <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
          <p class="muted text-xs">
            Showing {{ start }}–{{ end }} of {{ data.total }} · page {{ data.page + 1 }} of
            {{ Math.max(data.pages, 1) }}
          </p>
          <div class="flex gap-2">
            <UButton
              color="neutral"
              variant="outline"
              size="sm"
              :disabled="page === 0 || catalog.isFetching.value"
              @click="previous"
              >Previous</UButton
            >
            <UButton
              color="neutral"
              variant="outline"
              size="sm"
              :disabled="data.last || catalog.isFetching.value"
              @click="next"
              >Next</UButton
            >
          </div>
        </div>
      </template>
    </UiAsyncState>
  </section>
</template>
