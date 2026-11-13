<script setup lang="ts">
import type { FleetTracker } from '../domain/registration'
import { useFleetDestinations, useFleetSetup } from '../composables/useFleetSetup'
import { errorMessage } from '../../core/domain/errors'

const props = defineProps<{ tracker: FleetTracker }>()
const open = defineModel<boolean>('open', { default: false })
const { reassignTracker, busy, failure } = useFleetSetup()
const destinations = useFleetDestinations(() => open.value)
const target = ref(''),
  confirmed = ref(false),
  source = ref('')
const options = computed(
  () => destinations.data.value?.filter((vehicle) => vehicle.id !== source.value) ?? [],
)
const ready = computed(
  () =>
    !busy.value &&
    !destinations.isFetching.value &&
    !destinations.isError.value &&
    confirmed.value &&
    options.value.some((vehicle) => vehicle.id === target.value),
)
const toast = useToast()
watch(
  open,
  (value) => {
    if (value) {
      target.value = ''
      confirmed.value = false
      source.value = props.tracker.vehicleId ?? ''
      failure.value = ''
    }
  },
  { immediate: true },
)
watch(target, () => {
  confirmed.value = false
})
async function submit() {
  if (!ready.value) return
  if (
    !(await reassignTracker(props.tracker.trackerId, {
      sourceVehicleId: source.value,
      targetVehicleId: target.value,
    }))
  )
    return
  open.value = false
  toast.add({
    title: 'Tracker reassigned',
    description: `Saved to vehicle ${target.value}.`,
    color: 'success',
  })
}
</script>

<template>
  <UModal
    v-model:open="open"
    title="Reassign unused tracker"
    :description="`Move ${tracker.trackerId} (device ${tracker.deviceId}) from ${source} within this tenant.`"
    :dismissible="!busy"
    :close="!busy"
  >
    <template #body>
      <form id="reassign-tracker" class="form-stack" @submit.prevent="submit">
        <p class="form-hint">
          Only unused trackers can move. Recorded fixes, raw data, device activity, commands or
          assignment history block a move. Neither vehicle may have an active trip. Device IDs stay
          unchanged.
        </p>
        <p class="form-hint">
          Correct the assignment before the device starts transmitting. Pending gateway packets
          cannot be detected here.
        </p>
        <p v-if="destinations.isPending.value" role="status">Loading destination vehicles…</p>
        <p v-if="destinations.isError.value" role="alert">
          {{ errorMessage(destinations.error.value, 'Destination vehicles could not load.') }}
          <UButton variant="link" @click="destinations.refetch()">Retry vehicles</UButton>
        </p>
        <label for="tracker-destination">Destination vehicle</label>
        <select
          id="tracker-destination"
          v-model="target"
          class="native-select w-full"
          :disabled="busy || destinations.isFetching.value || destinations.isError.value"
        >
          <option value="">Choose a vehicle</option>
          <option v-for="vehicle in options" :key="vehicle.id" :value="vehicle.id">
            {{ vehicle.name }} · {{ vehicle.id }}
          </option>
        </select>
        <label class="flex items-start gap-2"
          ><input v-model="confirmed" type="checkbox" :disabled="busy || !target" /> I confirm this
          unused tracker should move to the selected vehicle.</label
        >
        <p v-if="failure" role="alert" class="form-error">{{ failure }}</p>
      </form>
    </template>
    <template #footer
      ><div class="modal-footer">
        <UButton color="neutral" variant="outline" :disabled="busy" @click="open = false"
          >Cancel</UButton
        >
        <UButton type="submit" form="reassign-tracker" :loading="busy" :disabled="!ready"
          >Confirm reassignment</UButton
        >
      </div></template
    >
  </UModal>
</template>
