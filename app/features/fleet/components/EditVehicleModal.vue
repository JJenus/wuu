<script setup lang="ts">
import type { Vehicle } from '../domain/fleet'
import { vehicleProfileSchema } from '#shared/fleet-management'
import { useFleetSetup } from '../composables/useFleetSetup'

const props = defineProps<{ vehicle: Vehicle }>()
const open = defineModel<boolean>('open', { default: false })
const { updateVehicle, busy, failure } = useFleetSetup()
const state = reactive({ model: '', licensePlate: '', vin: '' })
const toast = useToast()
watch(open, (value) => {
  if (value) {
    state.model = props.vehicle.profile?.model ?? ''
    state.licensePlate = props.vehicle.profile?.licensePlate ?? ''
    state.vin = props.vehicle.profile?.vin ?? ''
    failure.value = ''
  }
})
async function submit() {
  const input = vehicleProfileSchema.parse({
    model: state.model.trim() || null,
    licensePlate: state.licensePlate.trim() || null,
    vin: state.vin.trim() || null,
  })
  if (!(await updateVehicle(props.vehicle.id, input))) return
  open.value = false
  toast.add({ title: 'Vehicle details saved', color: 'success' })
}
</script>

<template>
  <UModal
    v-model:open="open"
    title="Edit vehicle"
    :description="`Update details for ${vehicle.id}. Vehicle and device IDs cannot be changed.`"
    :dismissible="!busy"
    :close="!busy"
  >
    <template #body>
      <UForm
        id="edit-vehicle"
        :state="state"
        :schema="vehicleProfileSchema"
        class="form-stack"
        @submit="submit"
      >
        <UFormField label="Model" name="model"
          ><UInput v-model="state.model" :maxlength="100" :disabled="busy" class="w-full"
        /></UFormField>
        <UFormField label="Registration" name="licensePlate"
          ><UInput v-model="state.licensePlate" :maxlength="20" :disabled="busy" class="w-full"
        /></UFormField>
        <UFormField label="VIN" name="vin"
          ><UInput v-model="state.vin" :maxlength="17" :disabled="busy" class="w-full"
        /></UFormField>
        <p class="form-hint">
          Blank fields clear the saved value. Telemetry, trips and tracker assignments are
          unchanged.
        </p>
        <p v-if="failure" role="alert" class="form-error">{{ failure }}</p>
      </UForm>
    </template>
    <template #footer
      ><div class="modal-footer">
        <UButton color="neutral" variant="outline" :disabled="busy" @click="open = false"
          >Cancel</UButton
        >
        <UButton type="submit" form="edit-vehicle" :loading="busy">Save changes</UButton>
      </div></template
    >
  </UModal>
</template>
