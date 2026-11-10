<script setup lang="ts">
import { useAssignedTrackers, useFleetSetup } from '../composables/useFleetSetup'
import { errorMessage } from '../../core/domain/errors'
import RegisterTrackerModal from './RegisterTrackerModal.vue'

const props = defineProps<{ vehicleId: string }>()
const { canAssign, canReadTrackers, accessError, retryAccess } = useFleetSetup()
const trackers = useAssignedTrackers(
  () => props.vehicleId,
  () => canReadTrackers.value,
)
const open = ref(false)
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
        </dl>
        <p v-if="!trackers.data.value?.length" class="muted">
          No trackers are registered to this vehicle.
        </p>
      </template>
      <p class="form-hint">
        Registration does not prove a hardware connection. Multiple trackers may be registered;
        unassign and reassign are not available.
      </p>
    </div>
    <RegisterTrackerModal :key="vehicleId" v-model:open="open" :vehicle-id="vehicleId" />
  </section>
</template>
