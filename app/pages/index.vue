<script setup lang="ts">
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import FleetMap from '~/features/fleet/components/FleetMap.vue'
import VehicleTable from '~/features/fleet/components/VehicleTable.vue'
import AddVehicleModal from '~/features/fleet/components/AddVehicleModal.vue'
import MetricCard from '~/features/workspace/components/MetricCard.vue'
const { vehicles, alerts, summary, isPending, isError, refetch } = useWorkspace()
const addOpen = ref(false)
const status = ref('all')
const visible = computed(() =>
  vehicles.value.filter((v) => status.value === 'all' || v.status === status.value).slice(0, 4),
)
const activeAlerts = computed(() => alerts.value.filter((a) => a.status === 'active'))
const tabs = computed(() => [
  { id: 'all', label: 'All vehicles', count: summary.value.total },
  { id: 'moving', label: 'Moving', count: summary.value.moving },
  { id: 'idle', label: 'Idle', count: summary.value.idle },
  { id: 'offline', label: 'Offline', count: summary.value.offline },
])
</script>

<template>
  <div class="overview-page">
    <UiPageHeading
      title="Your fleet, in focus."
      description="A little visibility. A lot of peace of mind."
      eyebrow="LET’S KEEP THINGS MOVING"
      ><UPopover :content="{ align: 'end' }"
        ><UButton
          icon="i-lucide-calendar-days"
          trailing-icon="i-lucide-chevron-down"
          color="neutral"
          variant="outline"
          class="date-button"
          >Oct 21, 2026</UButton
        ><template #content
          ><div class="date-explanation">
            <strong>A snapshot of a day on the road</strong>
            <p>
              This preview uses a fixed sample day. Date-range reporting will be connected to the
              trips API in a later slice.
            </p>
          </div></template
        ></UPopover
      ><UButton icon="i-lucide-plus" @click="addOpen = true">Add vehicle</UButton></UiPageHeading
    >
    <div class="preview-notice">
      <span
        ><UIcon name="i-lucide-flask-conical" /><strong>A look at what’s ahead.</strong> You’re
        exploring an interactive preview with sample fleet data.</span
      ><NuxtLink to="/settings"
        >About this workspace<UIcon name="i-lucide-arrow-up-right"
      /></NuxtLink>
    </div>
    <UiEmptyState
      v-if="isError"
      title="We couldn’t load your workspace"
      description="Please try again."
      ><UButton @click="refetch()">Retry</UButton></UiEmptyState
    >
    <div v-else-if="isPending" class="metric-grid" aria-label="Loading workspace">
      <USkeleton v-for="n in 4" :key="n" class="h-36 rounded-xl" />
    </div>
    <template v-else>
      <section class="metric-grid" aria-label="Fleet summary">
        <MetricCard
          label="Total vehicles"
          :value="summary.total"
          icon="i-lucide-truck"
          detail="All accounted for"
          note="in your fleet"
          chart="M0 31L12 31L20 25L31 25L40 17L51 19L61 11L74 11L85 7L100 7"
          featured
        />
        <MetricCard
          label="On the move"
          :value="summary.moving"
          icon="i-lucide-navigation"
          :detail="`${Math.round((summary.moving / (summary.total || 1)) * 100)}% of your fleet`"
          note="on the road"
          trend="positive"
          chart="M0 32L11 29L20 33L31 20L41 25L52 15L64 20L74 10L85 15L100 5"
        />
        <MetricCard
          label="Needs attention"
          :value="activeAlerts.length"
          icon="i-lucide-bell-ring"
          :detail="`${activeAlerts.filter((a) => a.severity === 'critical').length} critical alert`"
          note="worth a closer look"
          trend="warning"
          chart="M0 11L12 11L20 20L31 17L42 17L53 28L64 21L76 26L87 26L100 26"
        />
        <MetricCard
          label="Distance traveled"
          :value="summary.distance.toLocaleString()"
          icon="i-lucide-route"
          detail="km across your fleet"
          note="sample day"
          chart="M0 35L10 31L20 31L31 22L41 25L51 17L61 18L71 10L84 12L100 3"
        />
      </section>
      <section class="overview-middle">
        <article class="panel map-panel">
          <div class="panel-heading">
            <div>
              <h2>
                Fleet at a glance <span class="subtle-badge"><span />Preview</span>
              </h2>
              <p>Your vehicles. One clear picture.</p>
            </div>
            <NuxtLink to="/map" class="text-link"
              >Open map<UIcon name="i-lucide-arrow-up-right"
            /></NuxtLink>
          </div>
          <FleetMap :vehicles="vehicles" />
        </article>
        <article class="panel attention-panel">
          <div class="panel-heading">
            <div>
              <h2>
                Needs your attention<span class="count-badge">{{ activeAlerts.length }}</span>
              </h2>
              <p>The important things, not all the noise.</p>
            </div>
          </div>
          <div class="attention-list">
            <NuxtLink
              v-for="alert in activeAlerts.slice(0, 3)"
              :key="alert.id"
              to="/alerts"
              class="attention-item"
              ><span class="alert-icon" :class="alert.severity"><UIcon :name="alert.icon" /></span>
              <div>
                <strong>{{ alert.title }}</strong
                ><span>{{ alert.vehicle }}<i>·</i>{{ alert.time }}</span>
                <p>{{ alert.description }}</p>
              </div>
              <UIcon name="i-lucide-chevron-right" /></NuxtLink
            ><UiEmptyState
              v-if="!activeAlerts.length"
              title="All clear"
              description="Your fleet has no active preview alerts."
              icon="i-lucide-circle-check"
            />
          </div>
          <NuxtLink to="/alerts" class="attention-footer"
            >View all alerts<UIcon name="i-lucide-arrow-right"
          /></NuxtLink>
        </article>
      </section>
      <section class="panel fleet-panel">
        <div class="panel-heading">
          <div>
            <h2>
              Your fleet<span class="subtle-badge">{{ summary.total }} vehicles</span>
            </h2>
            <p>A quick check-in on everyone out there.</p>
          </div>
          <NuxtLink to="/vehicles" class="text-link"
            >View all vehicles<UIcon name="i-lucide-arrow-up-right"
          /></NuxtLink>
        </div>
        <div class="table-tabs" role="group" aria-label="Filter fleet by status">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            :class="{ active: status === tab.id }"
            :aria-pressed="status === tab.id"
            @click="status = tab.id"
          >
            {{ tab.label }}<span>{{ tab.count }}</span></button
          ><span class="table-preview-note"
            ><UIcon name="i-lucide-clock-3" />Sample snapshot · 11:06 AM</span
          >
        </div>
        <VehicleTable :vehicles="visible" compact />
        <div class="table-footer">
          <span>Showing {{ visible.length }} of {{ vehicles.length }} vehicles</span
          ><NuxtLink to="/vehicles"
            >See the whole picture<UIcon name="i-lucide-arrow-right"
          /></NuxtLink>
        </div>
      </section>
    </template>
    <AddVehicleModal v-model:open="addOpen" />
  </div>
</template>
