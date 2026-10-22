<script setup lang="ts">
import { errorMessage } from '~/features/core/domain/errors'
import { useDataSource } from '~/features/core/composables/useDataSource'
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import type { AlertState } from '~/features/workspace/domain/types'
const source = useDataSource()
const { alerts, updateAlert, isPending, isError, error, refetch } = useWorkspace()
const filter = ref('active')
const busy = ref<string | null>(null)
const toast = useToast()
const visible = computed(() =>
  alerts.value.filter((a) => filter.value === 'all' || a.status === filter.value),
)
async function change(id: string, status: AlertState) {
  if (!source.isPreview) return
  busy.value = id
  try {
    await updateAlert(id, status)
    toast.add({
      title: status === 'resolved' ? 'Alert resolved in preview' : 'Alert acknowledged in preview',
      color: 'success',
      icon: 'i-lucide-circle-check',
    })
  } catch (updateError) {
    toast.add({
      title: 'Unable to update alert',
      description: errorMessage(updateError),
      color: 'error',
    })
  } finally {
    busy.value = null
  }
}
useHead({ title: 'Alerts — Wuu' })
</script>

<template>
  <div>
    <UiPageHeading
      title="The right things, in sight."
      description="Less noise. More time to focus on what needs you."
      eyebrow="FLEET ALERTS"
      ><span class="subtle-badge">{{
        source.isPreview ? 'Preview actions · Session only' : 'Live alert feed'
      }}</span></UiPageHeading
    >
    <UiAsyncState
      :pending="isPending && !alerts.length"
      :error="isError && !alerts.length ? error : null"
      loading-label="Loading fleet alerts…"
      error-title="Alerts could not load"
      @retry="refetch"
    >
      <div class="filter-pills" role="group" aria-label="Filter alerts">
        <button
          v-for="item in ['active', 'acknowledged', 'resolved', 'all']"
          :key="item"
          :class="{ active: filter === item }"
          :aria-pressed="filter === item"
          @click="filter = item"
        >
          {{ item.charAt(0).toUpperCase() + item.slice(1)
          }}<span class="ml-2 opacity-60">{{
            alerts.filter((a) => item === 'all' || a.status === item).length
          }}</span>
        </button>
      </div>
      <div class="alert-cards">
        <article v-for="alert in visible" :key="alert.id" class="panel alert-card">
          <span class="alert-icon" :class="alert.severity"><UIcon :name="alert.icon" /></span>
          <div class="alert-card-content">
            <h3>{{ alert.title }}<UiStatusBadge :status="alert.severity" /></h3>
            <p>{{ alert.description }}</p>
            <div class="alert-meta">
              <NuxtLink
                v-if="alert.vehicleId"
                :to="`/vehicles/${alert.vehicleId}`"
                class="text-primary"
                >{{ alert.vehicle ?? alert.vehicleId }}</NuxtLink
              >
              <span v-else class="muted">{{ alert.vehicle ?? 'Vehicle not recorded' }}</span
              ><span class="muted"
                >{{ alert.time }} · {{ source.isPreview ? 'Sample event' : 'Fleet event' }}</span
              ><UiStatusBadge :status="alert.status" />
            </div>
          </div>
          <div class="alert-card-actions">
            <UButton
              :to="`/alerts/${alert.id}`"
              :aria-label="`View alert ${alert.id}`"
              color="neutral"
              variant="ghost"
              size="sm"
              icon="i-lucide-arrow-up-right"
              >Details</UButton
            >
            <template v-if="source.isPreview">
              <UButton
                v-if="alert.status === 'active'"
                color="neutral"
                variant="outline"
                size="sm"
                :loading="busy === alert.id"
                @click="change(alert.id, 'acknowledged')"
                >Acknowledge</UButton
              ><UButton
                v-if="alert.status !== 'resolved'"
                color="primary"
                variant="soft"
                size="sm"
                icon="i-lucide-check"
                :loading="busy === alert.id"
                @click="change(alert.id, 'resolved')"
                >Resolve</UButton
              >
            </template>
            <span v-else class="muted text-xs">Status changes require a verified operator.</span>
          </div>
        </article>
        <UiEmptyState
          v-if="!visible.length"
          title="A little peace of mind."
          description="There are no alerts in this view. Try another filter to see the rest."
          icon="i-lucide-circle-check"
        />
      </div>
    </UiAsyncState>
  </div>
</template>
