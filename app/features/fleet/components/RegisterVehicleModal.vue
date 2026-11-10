<script setup lang="ts">
import { vehicleRegistrationSchema } from '../domain/registration'
import { useFleetSetup } from '../composables/useFleetSetup'

const open = defineModel<boolean>('open', { default: false })
const { registerVehicle, busy, failure } = useFleetSetup()
const state = reactive({ vehicleId: '', model: '', licensePlate: '', vin: '' })
const inputs = [
  { key: 'vehicleId', label: 'Vehicle ID', max: 50, required: true },
  { key: 'model', label: 'Model', max: 100, required: false },
  { key: 'licensePlate', label: 'Registration', max: 20, required: false },
  { key: 'vin', label: 'VIN', max: 17, required: false },
] as const
watch(open, (value) => {
  if (value) {
    Object.assign(state, { vehicleId: '', model: '', licensePlate: '', vin: '' })
    failure.value = ''
  }
})
async function submit() {
  const parsed = vehicleRegistrationSchema.safeParse(state)
  if (!parsed.success) return
  const created = await registerVehicle(parsed.data)
  if (!created) return
  open.value = false
  await navigateTo(`/vehicles/${encodeURIComponent(created.id)}`)
}
</script>

<template>
  <UModal
    v-model:open="open"
    title="Add a live vehicle"
    description="Save a vehicle, then register its tracker from the vehicle details."
    :dismissible="!busy"
    :close="!busy"
  >
    <template #body>
      <UForm
        id="register-vehicle"
        :state="state"
        :schema="vehicleRegistrationSchema"
        class="form-stack"
        @submit="submit"
      >
        <UFormField
          v-for="field in inputs"
          :key="field.key"
          :label="field.label"
          :name="field.key"
          :required="field.required"
        >
          <UInput
            v-model="state[field.key]"
            :maxlength="field.max"
            :disabled="busy"
            class="w-full"
            :autofocus="field.key === 'vehicleId'"
          />
        </UFormField>
        <p class="form-hint">
          Use a unique, permanent vehicle ID. Registration and VIN are optional. Creating a vehicle
          does not bring hardware online.
        </p>
        <p v-if="failure" role="alert" class="form-error">{{ failure }}</p>
      </UForm>
    </template>
    <template #footer
      ><div class="modal-footer">
        <UButton color="neutral" variant="outline" :disabled="busy" @click="open = false"
          >Cancel</UButton
        >
        <UButton type="submit" form="register-vehicle" :loading="busy">Save vehicle</UButton>
      </div></template
    >
  </UModal>
</template>
