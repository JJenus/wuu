<script setup lang="ts">
import type { FleetTracker } from '../domain/registration'
import { trackerProfileSchema } from '#shared/fleet-management'
import { useFleetSetup } from '../composables/useFleetSetup'

const props = defineProps<{ tracker: FleetTracker }>()
const open = defineModel<boolean>('open', { default: false })
const { updateTracker, busy, failure } = useFleetSetup()
const state = reactive({ model: '', protocol: '', firmwareVersion: '', simNumber: '' })
const fields = [
  { key: 'model', label: 'Tracker model', max: 100 },
  { key: 'protocol', label: 'Protocol', max: 20 },
  { key: 'firmwareVersion', label: 'Firmware version', max: 50 },
  { key: 'simNumber', label: 'SIM number', max: 20 },
] as const
const toast = useToast()
watch(
  open,
  (value) => {
    if (value) {
      for (const field of fields) state[field.key] = props.tracker[field.key] ?? ''
      failure.value = ''
    }
  },
  { immediate: true },
)
async function submit() {
  const input = trackerProfileSchema.parse({
    model: state.model.trim() || null,
    protocol: state.protocol.trim() || null,
    firmwareVersion: state.firmwareVersion.trim() || null,
    simNumber: state.simNumber.trim() || null,
  })
  if (!(await updateTracker(props.tracker.trackerId, input))) return
  open.value = false
  toast.add({ title: 'Tracker details saved', color: 'success' })
}
</script>

<template>
  <UModal
    v-model:open="open"
    title="Edit tracker"
    :description="`Tracker ${tracker.trackerId} · Device ${tracker.deviceId}. Identities and measured status are not editable.`"
    :dismissible="!busy"
    :close="!busy"
  >
    <template #body>
      <UForm
        id="edit-tracker"
        :state="state"
        :schema="trackerProfileSchema"
        class="form-stack"
        @submit="submit"
      >
        <UFormField v-for="field in fields" :key="field.key" :label="field.label" :name="field.key">
          <UInput
            v-model="state[field.key]"
            :maxlength="field.max"
            :disabled="busy"
            class="w-full"
          />
        </UFormField>
        <p class="form-hint">
          Blank fields clear metadata. Editing protocol metadata does not reconfigure the device.
        </p>
        <p v-if="failure" role="alert" class="form-error">{{ failure }}</p>
      </UForm>
    </template>
    <template #footer
      ><div class="modal-footer">
        <UButton color="neutral" variant="outline" :disabled="busy" @click="open = false"
          >Cancel</UButton
        >
        <UButton type="submit" form="edit-tracker" :loading="busy">Save changes</UButton>
      </div></template
    >
  </UModal>
</template>
