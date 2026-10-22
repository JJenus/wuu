<script setup lang="ts">
import GeofenceEditor from '~/features/geofences/components/GeofenceEditor.vue'
import { useDataSource } from '~/features/core/composables/useDataSource'
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
const route = useRoute()
const source = useDataSource()
const { geofences, isPending, isError, error, refetch } = useWorkspace()
const zone = computed(() => geofences.value.find((z) => z.id === route.params.id))
</script>
<template>
  <UiAsyncState
    :pending="isPending && !zone"
    :error="isError && !zone ? error : null"
    loading-label="Loading this geofence…"
    error-title="This geofence could not load"
    @retry="refetch"
  >
    <GeofenceEditor v-if="zone && source.isPreview" :key="zone.id" :id="zone.id" :initial="zone" />
    <UiEmptyState
      v-else-if="zone"
      title="Live geofence editing is disabled"
      description="The current backend update contract can lose polygon geometry or multi-vehicle assignments."
      icon="i-lucide-shield-alert"
      ><UButton :to="`/geofences/${zone.id}`">Back to geofence</UButton></UiEmptyState
    >
    <UiEmptyState
      v-else
      title="Geofence not found"
      description="This zone is not part of the current session, or it was reset on reload."
      ><UButton to="/geofences">Back to geofences</UButton></UiEmptyState
    >
  </UiAsyncState>
</template>
