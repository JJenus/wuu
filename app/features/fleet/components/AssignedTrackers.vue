<script setup lang="ts">
import { useAssignedTrackers, useFleetSetup } from '../composables/useFleetSetup'
import { errorMessage } from '../../core/domain/errors'
import RegisterTrackerModal from './RegisterTrackerModal.vue'
import EditTrackerModal from './EditTrackerModal.vue'
import ReassignTrackerModal from './ReassignTrackerModal.vue'
import type { FleetTracker } from '../domain/registration'

const props = defineProps<{ vehicleId: string }>()
const { canAssign, canReadTrackers, accessError, retryAccess } = useFleetSetup()
const trackers = useAssignedTrackers(
  () => props.vehicleId,
  () => canReadTrackers.value,
)
const open = ref(false)
const selected = ref<FleetTracker | null>(null)
const editOpen = ref(false),
  moveOpen = ref(false)
function edit(tracker: FleetTracker) {
  selected.value = tracker
  editOpen.value = true
}
function reassign(tracker: FleetTracker) {
  selected.value = tracker
  moveOpen.value = true
}
</script>

<template>
  <section class="panel mt-6" aria-label="Assigned trackers">
    <div class="panel-heading">
      <h2>Assigned trackers</h2>
      <UButton v-if="canAssign" size="sm" icon="i-lucide-plus" @click="open = true"
        >Register tracker</UButton
      >
    </div>
    <div class="section-body detail-stack">
      <p v-if="accessError" role="alert">
        Tracker permissions could not be checked.
        <UButton variant="link" @click="retryAccess">Retry permissions</UButton>
      </p>
      <p v-else-if="!canReadTrackers" class="muted">
        Tracker records require device-read permission.
      </p>
      <p v-else-if="trackers.isPending.value" role="status">Loading assigned trackers…</p>
      <p v-else-if="trackers.isError.value" role="alert">
        {{ errorMessage(trackers.error.value, 'Assigned trackers could not load.') }}
        <UButton variant="link" @click="trackers.refetch()">Retry trackers</UButton>
      </p>
      <template v-else>
        <dl
          v-for="tracker in trackers.data.value"
          :key="tracker.trackerId"
          class="detail-info"
          :aria-label="`Tracker ${tracker.trackerId}`"
        >
          <div>
            <dt>Tracker ID</dt>
            <dd>{{ tracker.trackerId }}</dd>
          </div>
          <div>
            <dt>Device ID</dt>
            <dd>{{ tracker.deviceId ?? 'Not provided' }}</dd>
          </div>
          <div>
            <dt>Model</dt>
            <dd>{{ tracker.model || 'Not provided' }}</dd>
          </div>
          <div>
            <dt>Protocol</dt>
            <dd>{{ tracker.protocol || 'Not provided' }}</dd>
          </div>
          <div>
            <dt>Firmware</dt>
            <dd>{{ tracker.firmwareVersion || 'Not provided' }}</dd>
          </div>
          <div>
            <dt>SIM number</dt>
            <dd>{{ tracker.simNumber || 'Not provided' }}</dd>
          </div>
          <div v-if="canAssign">
            <dt>Manage</dt>
            <dd class="flex flex-wrap gap-2">
              <UButton
                size="sm"
                variant="outline"
                :aria-label="`Edit tracker ${tracker.trackerId}`"
                @click="edit(tracker)"
                >Edit details</UButton
              >
              <UButton
                size="sm"
                variant="outline"
                :aria-label="`Reassign tracker ${tracker.trackerId}`"
                @click="reassign(tracker)"
                >Reassign unused tracker</UButton
              >
            </dd>
          </div>
        </dl>
        <p v-if="!trackers.data.value?.length" class="muted">
          No trackers are registered to this vehicle.
        </p>
      </template>
      <p class="form-hint">
        Registration does not prove a hardware connection. Multiple trackers may be registered; only
        unused trackers can be reassigned within this tenant. Unassignment is unavailable.
      </p>
    </div>
    <RegisterTrackerModal :key="vehicleId" v-model:open="open" :vehicle-id="vehicleId" />
    <EditTrackerModal v-if="selected" v-model:open="editOpen" :tracker="selected" />
    <ReassignTrackerModal v-if="selected" v-model:open="moveOpen" :tracker="selected" />
  </section>
</template>
