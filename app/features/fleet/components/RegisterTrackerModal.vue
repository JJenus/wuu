<script setup lang="ts">
import { trackerRegistrationSchema } from '../domain/registration'
import { useFleetSetup } from '../composables/useFleetSetup'

const props = defineProps<{ vehicleId: string }>()
const open = defineModel<boolean>('open', { default: false })
const { registerTracker, busy, failure } = useFleetSetup()
const schema = trackerRegistrationSchema.omit({ vehicleId: true })
const empty = () => ({
  trackerId: '',
  deviceId: '',
  model: '',
  protocol: '',
  firmwareVersion: '',
  simNumber: '',
})
const state = reactive(empty())
const toast = useToast()
const inputs = [
  { key: 'trackerId', label: 'Tracker ID', max: 50, required: true },
  { key: 'deviceId', label: 'Device ID', max: 50, required: true },
  { key: 'model', label: 'Tracker model', max: 100, required: false },
  { key: 'protocol', label: 'Protocol', max: 20, required: false },
  { key: 'firmwareVersion', label: 'Firmware version', max: 50, required: false },
  { key: 'simNumber', label: 'SIM number', max: 20, required: false },
] as const
watch(open, (value) => {
  if (value) {
    Object.assign(state, empty())
    failure.value = ''
  }
})
async function submit() {
  const parsed = trackerRegistrationSchema.safeParse({ ...state, vehicleId: props.vehicleId })
  if (!parsed.success) return
  const created = await registerTracker(parsed.data)
  if (!created) return
  open.value = false
  toast.add({
    title: 'Tracker registered',
    description: `Saved to vehicle ${props.vehicleId}. Telemetry is not yet verified.`,
    color: 'success',
  })
}
</script>

<template>
  <UModal
    v-model:open="open"
    title="Register tracker"
    :description="`Register a device already assigned to vehicle ${vehicleId}.`"
    :dismissible="!busy"
    :close="!busy"
  >
    <template #body>
      <UForm
        id="register-tracker"
        :state="state"
        :schema="schema"
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
            :autofocus="field.key === 'trackerId'"
          />
        </UFormField>
        <p class="form-hint">
          Device IDs must be unique across the fleet. This registers the assignment only, not a
          connection or hardware attestation. Reassignment is not available.
        </p>
        <p v-if="failure" role="alert" class="form-error">{{ failure }}</p>
      </UForm>
    </template>
    <template #footer
      ><div class="modal-footer">
        <UButton color="neutral" variant="outline" :disabled="busy" @click="open = false"
          >Cancel</UButton
        >
        <UButton type="submit" form="register-tracker" :loading="busy">Save tracker</UButton>
      </div></template
    >
  </UModal>
</template>
