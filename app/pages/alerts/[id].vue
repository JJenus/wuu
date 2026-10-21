<script setup lang="ts">
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import type { AlertState } from '~/features/workspace/domain/types'
const route = useRoute()
const { alerts, vehicles, updateAlert } = useWorkspace()
const alert = computed(() => alerts.value.find((a) => a.id === route.params.id))
const vehicle = computed(() => vehicles.value.find((v) => v.id === alert.value?.vehicleId))
const busy = ref(false)
const error = ref('')
const toast = useToast()
async function change(status: AlertState) {
  if (!alert.value || busy.value) return
  const id = alert.value.id
  busy.value = true
  error.value = ''
  try {
    await updateAlert(id, status)
    toast.add({
      title: `Alert ${status} in preview`,
      description: 'This change resets on reload. No notification was sent.',
      color: 'success',
    })
  } catch {
    error.value = 'Unable to update this preview alert. Please try again.'
  } finally {
    busy.value = false
  }
}
useHead(() => ({ title: `${alert.value?.title || 'Alert not found'} — Wayline` }))
</script>

<template>
  <div class="detail-page">
    <NuxtLink to="/alerts" class="back-link"
      ><UIcon name="i-lucide-arrow-left" />Back to alerts</NuxtLink
    >
    <template v-if="alert">
      <UiPageHeading
        :title="alert.title"
        :description="`${alert.id} · ${alert.vehicle} · Sample event`"
        eyebrow="ALERT DETAILS"
      >
        <UiStatusBadge :status="alert.severity" /><span data-testid="alert-state" role="status"
          ><UiStatusBadge :status="alert.status"
        /></span>
      </UiPageHeading>
      <div class="info-banner">
        <UIcon name="i-lucide-flask-conical" />
        <p>
          Preview actions only. Status changes stay in this session and reset on reload. No message
          or device command is sent.
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
                  <dd>{{ alert.time }} · Sample snapshot</dd>
                </div>
                <div>
                  <dt>Source</dt>
                  <dd>Fictional fleet event</dd>
                </div>
                <div>
                  <dt>Vehicle</dt>
                  <dd>{{ alert.vehicle }}</dd>
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
                    Check the vehicle context before deciding what needs attention. This preview
                    does not provide fresh telemetry.
                  </p>
                </div>
              </li>
              <li>
                <span class="step-number">2</span>
                <div>
                  <h3>Acknowledge the event</h3>
                  <p>
                    Mark the sample alert as seen. This does not assign an operator or contact the
                    driver.
                  </p>
                </div>
              </li>
              <li>
                <span class="step-number">3</span>
                <div>
                  <h3>Resolve after review</h3>
                  <p>
                    Resolution updates the preview status only. It does not verify that the
                    underlying condition has cleared.
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
                    <dd>{{ vehicle.location }}</dd>
                  </div>
                  <div>
                    <dt>Last update</dt>
                    <dd>{{ vehicle.updated }} (sample)</dd>
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
              <p v-else class="muted">The related vehicle is not available in this preview.</p>
            </div>
          </section>
          <section class="panel">
            <div class="panel-heading"><h2>Preview response</h2></div>
            <div class="section-body detail-stack">
              <p v-if="alert.status === 'resolved'" class="muted">
                This sample alert is resolved. Reloading restores its original state.
              </p>
              <p v-else class="muted">Update this alert across your preview workspace.</p>
              <p v-if="error" role="alert" class="form-error">{{ error }}</p>
              <UButton
                v-if="alert.status === 'active'"
                color="neutral"
                variant="outline"
                :loading="busy"
                @click="change('acknowledged')"
                >Acknowledge in preview</UButton
              >
              <UButton
                v-if="alert.status !== 'resolved'"
                icon="i-lucide-check"
                :loading="busy"
                @click="change('resolved')"
                >Resolve in preview</UButton
              >
            </div>
          </section>
        </div>
      </div>
    </template>
    <UiEmptyState
      v-else
      title="Alert not found"
      description="This alert isn’t part of the preview workspace."
      icon="i-lucide-bell-off"
      ><UButton to="/alerts">Back to alerts</UButton></UiEmptyState
    >
  </div>
</template>
