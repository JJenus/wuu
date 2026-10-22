<script setup lang="ts">
import GeofenceEditor from '~/features/geofences/components/GeofenceEditor.vue'
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
const route = useRoute()
const { geofences } = useWorkspace()
const zone = computed(() => geofences.value.find((z) => z.id === route.params.id))
</script>
<template>
  <GeofenceEditor v-if="zone" :key="zone.id" :id="zone.id" :initial="zone" /><UiEmptyState
    v-else
    title="Geofence not found"
    description="This zone is not part of the preview session, or was reset on reload."
    ><UButton to="/geofences">Back to geofences</UButton></UiEmptyState
  >
</template>
