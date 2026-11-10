<script setup lang="ts">
import GeofenceEditor from '~/features/geofences/components/GeofenceEditor.vue'
import { useGeofenceAccess } from '~/features/geofences/composables/useGeofenceAccess'
import type { GeofenceDraft } from '~/features/geofences/domain/geofence'
const { source, allowed, message, retry } = useGeofenceAccess()
// Keep an admitted draft mounted when a later permission refresh fails.
const admitted = ref(false)
watchEffect(() => {
  if (allowed.value) admitted.value = true
})
const initial: GeofenceDraft = {
  name: '',
  description: '',
  location: '',
  active: true,
  events: source.isPreview ? 'Entry and exit' : '',
  vehicleIds: [],
  geometry: null,
}
</script>
<template>
  <GeofenceEditor v-if="allowed || admitted" :initial="initial" />
  <UiEmptyState
    v-else
    title="Geofence editor unavailable"
    :description="message"
    icon="i-lucide-shield-alert"
    ><UButton color="neutral" variant="outline" @click="retry">Check permissions again</UButton>
    <UButton to="/geofences">Back to geofences</UButton></UiEmptyState
  >
</template>
