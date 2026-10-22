<script setup lang="ts">
import GeofenceEditor from '~/features/geofences/components/GeofenceEditor.vue'
import type { GeofenceDraft } from '~/features/geofences/domain/geofence'
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
<template><GeofenceEditor :initial="initial" /></template>
