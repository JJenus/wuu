<script setup lang="ts">
import GeofenceEditor from '~/features/geofences/components/GeofenceEditor.vue'
import { useGeofenceAccess } from '~/features/geofences/composables/useGeofenceAccess'
import { useWorkspaceRecord } from '~/features/workspace/composables/useWorkspaceRecord'
const route = useRoute()
const { allowed, message, retry } = useGeofenceAccess()
const {
  data: zone,
  isPending,
  isError,
  error,
  refetch,
} = useWorkspaceRecord('geofences', () => String(route.params.id))
</script>
<template>
  <UiAsyncState
    :pending="isPending && !zone"
    :error="isError && !zone ? error : null"
    loading-label="Loading this geofence…"
    error-title="This geofence could not load"
    @retry="refetch"
  >
    <GeofenceEditor v-if="zone && allowed" :key="zone.id" :id="zone.id" :initial="zone" />
    <UiEmptyState
      v-else-if="zone"
      title="Geofence editor unavailable"
      :description="message"
      icon="i-lucide-shield-alert"
      ><UButton color="neutral" variant="outline" @click="retry">Check permissions again</UButton>
      <UButton :to="`/geofences/${zone.id}`">Back to geofence</UButton></UiEmptyState
    >
    <UiEmptyState
      v-else
      title="Geofence not found"
      description="This zone is not part of the current session, or it was reset on reload."
      ><UButton to="/geofences">Back to geofences</UButton></UiEmptyState
    >
  </UiAsyncState>
</template>
