<script setup lang="ts">
import { useDataSource } from '~/features/core/composables/useDataSource'
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import { geometrySummary } from '~/features/geofences/domain/geofence'
const source = useDataSource()
const { geofences, isPending, isError, error, refetch } = useWorkspace('geofences')
useHead({ title: 'Geofences — Wuu' })
</script>

<template>
  <div>
    <UiPageHeading
      title="Your places, connected."
      description="Draw a little context around the places that matter."
      eyebrow="GEOFENCES"
    >
      <UButton
        to="/geofences/new"
        icon="i-lucide-plus"
        :disabled="!source.isPreview"
        :title="
          source.isPreview
            ? undefined
            : 'Live geofence create and edit are disabled until the full geometry contract is verified'
        "
        >Create geofence</UButton
      >
    </UiPageHeading>
    <div class="info-banner">
      <UIcon name="i-lucide-info" />
      <p v-if="source.isPreview">
        Draw, edit, and assign sample geofences in this session. Changes reset on reload. No real
        boundary monitoring or notifications are activated.
      </p>
      <p v-else>
        Geofences are loaded from the live service. Live create and edit remain disabled until the
        backend contract can preserve polygon geometry and multi-vehicle assignments.
      </p>
    </div>
    <UiAsyncState
      :pending="isPending && !geofences.length"
      :error="isError && !geofences.length ? error : null"
      loading-label="Loading geofences…"
      error-title="Geofences could not load"
      @retry="refetch"
    >
      <div v-if="geofences.length" class="card-grid">
        <article v-for="zone in geofences" :key="zone.id" class="panel geofence-card">
          <div class="zone-illustration">
            <span><UIcon :name="zone.icon" /></span>
          </div>
          <h3>
            {{ zone.name
            }}<span class="preview-tag">{{ source.isPreview ? 'Sample zone' : 'Live zone' }}</span>
          </h3>
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
      <UiEmptyState
        v-else
        title="No geofences yet"
        description="Boundaries will appear here when they are available for this workspace."
        icon="i-lucide-scan"
      />
    </UiAsyncState>
  </div>
</template>
