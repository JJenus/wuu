<script setup lang="ts">
import { useEffectiveAccess } from '../composables/useEffectiveAccess'
const access = useEffectiveAccess()
</script>

<template>
  <section class="panel p-6 mb-6" aria-labelledby="effective-access-heading">
    <h2 id="effective-access-heading" class="text-xl font-semibold text-highlighted">
      Current server access
    </h2>
    <p class="text-muted my-3">
      These are request-time permissions for the active identity, not role-name templates. The
      server rechecks authorization on every action; this snapshot does not grant access.
    </p>
    <UiAsyncState
      :pending="access.isPending.value"
      :error="access.error.value"
      loading-label="Checking current access…"
      error-title="Current access could not load"
      @retry="access.refetch()"
    >
      <template v-if="access.data.value">
        <p>
          User {{ access.data.value.userId }} ·
          {{
            access.data.value.organizationId === null
              ? 'No active tenant'
              : `Tenant ${access.data.value.organizationId}`
          }}
        </p>
        <p class="text-muted">Access expires at {{ access.data.value.accessExpiresAt }} (UTC)</p>
        <ul class="flex flex-wrap gap-2 my-4" aria-label="Effective permissions">
          <li
            v-for="permission in access.data.value.permissions"
            :key="permission"
            class="rounded border border-default bg-muted px-2 py-1 text-sm"
          >
            {{ permission }}
          </li>
        </ul>
        <p v-if="access.data.value.permissions.length === 0" class="text-muted">
          No effective permissions in the current context.
        </p>
        <UButton
          variant="outline"
          color="neutral"
          :loading="access.isFetching.value"
          @click="access.refetch()"
          >Recheck access</UButton
        >
      </template>
    </UiAsyncState>
  </section>
</template>
