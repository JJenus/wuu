<script setup lang="ts">
import AlertResponseActions from '~/features/alerts/components/AlertResponseActions.vue'
import { useDataSource } from '~/features/core/composables/useDataSource'
import { formatDateTime } from '~/features/core/domain/time'
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import { useWorkspaceRecord } from '~/features/workspace/composables/useWorkspaceRecord'
const route = useRoute()
const source = useDataSource()
const { vehicles } = useWorkspace('alerts')
const {
  data: alert,
  isPending,
  isError,
  error,
  refetch,
} = useWorkspaceRecord('alerts', () => String(route.params.id))
const vehicle = computed(() => vehicles.value.find((v) => v.id === alert.value?.vehicleId))
useHead(() => ({ title: `${alert.value?.title || 'Alert not found'} — Wuu` }))
</script>

<template>
  <div class="detail-page">
    <NuxtLink to="/alerts" class="back-link"
      ><UIcon name="i-lucide-arrow-left" />Back to alerts</NuxtLink
    >
    <UiAsyncState
      :pending="isPending && !alert"
      :error="isError && !alert ? error : null"
      loading-label="Loading this alert…"
      error-title="This alert could not load"
      @retry="refetch"
    >
      <template v-if="alert">
        <div v-if="isError" role="alert" class="info-banner">
          <p>Alert refresh failed. Displayed state may be out of date.</p>
          <UButton color="neutral" variant="outline" @click="refetch()"
            >Retry alert refresh</UButton
          >
        </div>
        <UiPageHeading
          :title="alert.title"
          :description="`${alert.id} · ${alert.vehicle ?? 'Vehicle not recorded'} · ${source.isPreview ? 'Sample event' : 'Fleet event'}`"
          eyebrow="ALERT DETAILS"
        >
          <UiStatusBadge :status="alert.severity" /><span data-testid="alert-state" role="status"
            ><UiStatusBadge :status="alert.status"
          /></span>
        </UiPageHeading>
        <div class="info-banner">
          <UIcon :name="source.isPreview ? 'i-lucide-flask-conical' : 'i-lucide-shield-check'" />
          <p v-if="source.isPreview">
            Preview actions only. Status changes stay in this session and reset on reload. No
            message or device command is sent.
          </p>
          <p v-else>
            Live responses require current server permission. Resolution records review notes and
            your authenticated identity; it does not send a notification or device command.
          </p>
        </div>
        <div class="detail-layout">
          <div class="detail-stack">
            <section class="panel">
              <div class="panel-heading">
                <h2>What happened</h2>
                <span class="alert-icon" :class="alert.severity"><UIcon :name="alert.icon" /></span>
              </div>
              <div class="section-body detail-stack">
                <p class="detail-lead">{{ alert.description }}</p>
                <dl class="detail-info">
                  <div>
                    <dt>Reported</dt>
                    <dd>
                      {{ alert.time
                      }}<template v-if="alert.triggeredAt">
                        · {{ formatDateTime(alert.triggeredAt) }}</template
                      >
                    </dd>
                  </div>
                  <div>
                    <dt>Source</dt>
                    <dd>
                      {{ source.isPreview ? 'Fictional fleet event' : 'Live alert service' }}
                    </dd>
                  </div>
                  <div>
                    <dt>Vehicle</dt>
                    <dd>{{ alert.vehicle ?? 'Not recorded' }}</dd>
                  </div>
                  <div>
                    <dt>Severity</dt>
                    <dd>{{ alert.severity }}</dd>
                  </div>
                </dl>
              </div>
            </section>
            <section class="panel">
              <div class="panel-heading">
                <h2>Response checklist</h2>
                <span class="preview-tag">Guidance, not an audit log</span>
              </div>
              <ol class="detail-steps section-body">
                <li>
                  <span class="step-number">1</span>
                  <div>
                    <h3>Review the last known readings</h3>
                    <p>
                      Check the linked vehicle context before deciding what needs attention. An
                      alert may predate the latest telemetry.
                    </p>
                  </div>
                </li>
                <li>
                  <span class="step-number">2</span>
                  <div>
                    <h3>Acknowledge the event</h3>
                    <p>
                      Mark the alert as seen. This does not assign an operator, contact the driver,
                      or clear the underlying condition.
                    </p>
                  </div>
                </li>
                <li>
                  <span class="step-number">3</span>
                  <div>
                    <h3>Resolve after review</h3>
                    <p>
                      Resolution records a status change only. Live resolution also requires
                      operator identity and review notes.
                    </p>
                  </div>
                </li>
              </ol>
            </section>
          </div>
          <div class="detail-stack">
            <section class="panel">
              <div class="panel-heading"><h2>Take a closer look</h2></div>
              <div class="section-body detail-stack">
                <template v-if="vehicle"
                  ><div class="detail-record">
                    <span class="vehicle-icon"><UIcon name="i-lucide-truck" /></span>
                    <div>
                      <strong>{{ vehicle.name }}</strong>
                      <p>{{ vehicle.plate }}</p>
                    </div>
                  </div>
                  <dl class="detail-info">
                    <div>
                      <dt>Last known location</dt>
                      <dd>{{ vehicle.location ?? 'Location not provided' }}</dd>
                    </div>
                    <div>
                      <dt>Last update</dt>
                      <dd>{{ vehicle.updated ?? 'No telemetry recorded' }}</dd>
                    </div>
                  </dl>
                  <UButton
                    :to="`/vehicles/${vehicle.id}`"
                    :aria-label="`View ${vehicle.name}`"
                    color="neutral"
                    variant="outline"
                    trailing-icon="i-lucide-arrow-up-right"
                    >View {{ vehicle.name }}</UButton
                  ></template
                >
                <p v-else class="muted">The related vehicle is not available in this workspace.</p>
              </div>
            </section>
            <section class="panel">
              <div class="panel-heading">
                <h2>{{ source.isPreview ? 'Preview response' : 'Response' }}</h2>
              </div>
              <div class="section-body detail-stack">
                <p v-if="alert.status === 'resolved'" class="muted">
                  This alert is resolved.<template v-if="source.isPreview">
                    Reloading restores its original preview state.</template
                  >
                </p>
                <p v-else-if="source.isPreview" class="muted">
                  Update this alert across your preview workspace.
                </p>
                <dl
                  v-if="!source.isPreview"
                  class="space-y-2 text-sm"
                  aria-label="Recorded alert attribution"
                >
                  <div>
                    <dt>Acknowledged by user ID</dt>
                    <dd>{{ alert.acknowledgedBy || 'Not recorded' }}</dd>
                  </div>
                  <div>
                    <dt>Acknowledged at (UTC)</dt>
                    <dd>{{ alert.acknowledgedAt || 'Not recorded' }}</dd>
                  </div>
                  <div>
                    <dt>Resolved by user ID</dt>
                    <dd>{{ alert.resolvedBy || 'Not recorded' }}</dd>
                  </div>
                  <div>
                    <dt>Resolved at (UTC)</dt>
                    <dd>{{ alert.resolvedAt || 'Not recorded' }}</dd>
                  </div>
                  <div>
                    <dt>Resolution notes</dt>
                    <dd class="whitespace-pre-wrap">
                      {{ alert.resolutionNotes || 'Not recorded' }}
                    </dd>
                  </div>
                </dl>
                <AlertResponseActions :id="alert.id" :status="alert.status" detail />
              </div>
            </section>
          </div>
        </div>
      </template>
      <UiEmptyState
        v-else
        title="Alert not found"
        description="This alert is not part of the current workspace."
        icon="i-lucide-bell-off"
        ><UButton to="/alerts">Back to alerts</UButton></UiEmptyState
      >
    </UiAsyncState>
  </div>
</template>
