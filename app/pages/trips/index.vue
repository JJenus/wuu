<script setup lang="ts">
import { useDataSource } from '~/features/core/composables/useDataSource'
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import { useTripExport } from '~/features/workspace/composables/useTripExport'
const source = useDataSource()
const { exportTrips } = useTripExport()
const { trips, isPending, isError, error, refetch } = useWorkspace()
const search = ref('')
const selected = ref<string | null>(null)
const detailOpen = ref(false)
const visible = computed(() =>
  trips.value.filter((t) =>
    `${t.vehicle ?? ''} ${t.driver ?? ''} ${t.from ?? ''} ${t.to ?? ''}`
      .toLowerCase()
      .includes(search.value.toLowerCase()),
  ),
)
const detail = computed(() => trips.value.find((t) => t.id === selected.value))
function show(id: string) {
  selected.value = id
  detailOpen.value = true
}
function exportVisible() {
  exportTrips(visible.value)
}
useHead({ title: 'Trips — Wuu' })
</script>

<template>
  <div>
    <UiPageHeading
      title="Every journey tells a story."
      description="Follow the routes, the stops, and everything in between."
      eyebrow="TRIP HISTORY"
      ><UButton
        icon="i-lucide-download"
        color="neutral"
        variant="outline"
        :disabled="isPending || !visible.length"
        @click="exportVisible"
        >{{ source.isPreview ? 'Export preview CSV' : 'Export filtered CSV' }}</UButton
      ></UiPageHeading
    >
    <div class="info-banner">
      <UIcon name="i-lucide-route" />
      <p v-if="source.isPreview">
        Showing sample journeys for October 21, 2026. The export includes only the filtered preview
        rows.
      </p>
      <p v-else>
        Trip records are loaded from the live service. The CSV includes only the filtered rows
        currently loaded in this view.
      </p>
    </div>
    <UiAsyncState
      :pending="isPending && !trips.length"
      :error="isError && !trips.length ? error : null"
      loading-label="Loading journeys…"
      error-title="Trip history could not load"
      @retry="refetch"
    >
      <section class="panel">
        <div class="toolbar">
          <UInput
            v-model="search"
            icon="i-lucide-search"
            placeholder="Search trips, places, or drivers..."
            aria-label="Search trips"
          /><span class="record-count"
            >{{ visible.length }} {{ source.isPreview ? 'sample ' : '' }}journeys</span
          >
        </div>
        <div class="table-scroll" role="region" aria-label="Trip records" tabindex="0">
          <table class="data-table">
            <thead>
              <tr>
                <th>Trip / vehicle</th>
                <th>Route</th>
                <th>Driver</th>
                <th>Started</th>
                <th>Distance</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              <tr v-for="trip in visible" :key="trip.id">
                <td>
                  <button class="vehicle-cell bg-transparent text-left" @click="show(trip.id)">
                    <span class="vehicle-icon"><UIcon name="i-lucide-route" /></span
                    ><span
                      ><strong>{{ trip.vehicle ?? 'Vehicle not recorded' }}</strong
                      ><small>{{ trip.id }}</small></span
                    >
                  </button>
                </td>
                <td>
                  <span v-if="trip.from || trip.to" class="route-cell"
                    >{{ trip.from ?? 'Origin not recorded' }}<UIcon name="i-lucide-arrow-right" />{{
                      trip.to ?? 'Destination not recorded'
                    }}</span
                  >
                  <span v-else class="muted">Route not recorded</span>
                </td>
                <td>{{ trip.driver ?? 'Not recorded' }}</td>
                <td>{{ trip.started }}</td>
                <td>{{ trip.distance }} km</td>
                <td><UiStatusBadge :status="trip.status" /></td>
                <td>
                  <UButton
                    icon="i-lucide-chevron-right"
                    variant="ghost"
                    color="neutral"
                    :to="`/trips/${trip.id}`"
                    :aria-label="`Open trip ${trip.id}`"
                  />
                </td>
              </tr>
            </tbody>
          </table>
          <UiEmptyState
            v-if="!visible.length"
            title="No journeys found"
            description="Try another vehicle, place, or driver name."
          />
        </div>
      </section>
    </UiAsyncState>
    <UModal
      v-model:open="detailOpen"
      :title="detail ? `Journey ${detail.id}` : 'Journey details'"
      :description="
        source.isPreview
          ? 'Sample trip details from the preview fleet.'
          : 'Trip details loaded from the live service.'
      "
      ><template #body
        ><template v-if="detail"
          ><dl class="detail-info">
            <div>
              <dt>Vehicle</dt>
              <dd>{{ detail.vehicle ?? 'Not recorded' }}</dd>
            </div>
            <div>
              <dt>Driver</dt>
              <dd>{{ detail.driver ?? 'Not recorded' }}</dd>
            </div>
            <div>
              <dt>From</dt>
              <dd>{{ detail.from ?? 'Not recorded' }}</dd>
            </div>
            <div>
              <dt>To</dt>
              <dd>{{ detail.to ?? 'Not recorded' }}</dd>
            </div>
            <div>
              <dt>Duration</dt>
              <dd>{{ detail.duration }}</dd>
            </div>
            <div>
              <dt>Distance</dt>
              <dd>{{ detail.distance }} km</dd>
            </div>
          </dl>
          <p class="form-hint mt-6">
            Route playback requires a historical telemetry API and is not available from the current
            trip contract.
          </p>
          <UButton :to="`/trips/${detail.id}`" class="mt-6" icon="i-lucide-arrow-up-right"
            >Open full trip details</UButton
          ></template
        ></template
      ></UModal
    >
  </div>
</template>
