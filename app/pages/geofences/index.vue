<script setup lang="ts">
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import { geometrySummary } from '~/features/geofences/domain/geofence'
const { geofences } = useWorkspace()
useHead({ title: 'Geofences — Wuu' })
</script>

<template>
  <div>
    <UiPageHeading
      title="Your places, connected."
      description="Draw a little context around the places that matter."
      eyebrow="GEOFENCES"
    >
      <UButton to="/geofences/new" icon="i-lucide-plus">Create geofence</UButton>
    </UiPageHeading>
    <div class="info-banner">
      <UIcon name="i-lucide-info" />
      <p>
        Draw, edit, and assign sample geofences in this session. Changes reset on reload. No real
        boundary monitoring or notifications are activated.
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
          <span
            >{{ geometrySummary(zone.geometry) }} · {{ zone.vehicleIds.length }} linked
            vehicles</span
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
