<script setup lang="ts">
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
const { geofences } = useWorkspace()
useHead({ title: 'Geofences — Wayline' })
</script>

<template>
  <div>
    <UiPageHeading
      title="Your places, connected."
      description="Draw a little context around the places that matter."
      eyebrow="GEOFENCES"
    >
      <span class="subtle-badge">Read-only concept preview</span>
    </UiPageHeading>
    <div class="info-banner">
      <UIcon name="i-lucide-info" />
      <p>
        These zones illustrate the geofence experience. Creating or editing zones will be enabled
        after the backend geometry and vehicle-scoping contracts are verified.
      </p>
    </div>
    <div class="card-grid">
      <article v-for="zone in geofences" :key="zone.id" class="panel geofence-card">
        <div class="zone-illustration">
          <span><UIcon :name="zone.icon" /></span>
        </div>
        <h3>{{ zone.name }}<span class="preview-tag">Sample zone</span></h3>
        <p>{{ zone.description }}</p>
        <footer>
          <span>{{ zone.radius }} radius · {{ zone.vehicleIds.length }} sample vehicles</span
          ><UButton
            :to="`/geofences/${zone.id}`"
            icon="i-lucide-arrow-up-right"
            color="neutral"
            variant="ghost"
            size="xs"
            :aria-label="`View ${zone.name}`"
          />
        </footer>
      </article>
    </div>
  </div>
</template>
