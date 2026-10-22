<script setup lang="ts">
import GeofenceEditor from '~/features/geofences/components/GeofenceEditor.vue'
import { useDataSource } from '~/features/core/composables/useDataSource'
import type { GeofenceDraft } from '~/features/geofences/domain/geofence'
const source = useDataSource()
const initial: GeofenceDraft = {
  name: '',
  description: '',
  location: '',
  active: true,
  events: 'Entry and exit',
  vehicleIds: [],
  geometry: null,
}
</script>
<template>
  <GeofenceEditor v-if="source.isPreview" :initial="initial" />
  <UiEmptyState
    v-else
    title="Live geofence creation is disabled"
    description="The current backend contract cannot safely preserve polygon geometry and multi-vehicle assignments. Use another configured workflow until that contract is verified."
    icon="i-lucide-shield-alert"
    ><UButton to="/geofences">Back to geofences</UButton></UiEmptyState
  >
</template>
