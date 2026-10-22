<script setup lang="ts">
import { errorMessage, isApiError } from '~/features/core/domain/errors'
import { useDataSource } from '~/features/core/composables/useDataSource'
import { validateVehicleFields, type NewVehicle } from '../domain/fleet'
import { useWorkspace } from '../../workspace/composables/useWorkspace'

const open = defineModel<boolean>('open', { default: false })
const name = ref('')
const plate = ref('')
const error = ref('')
const fields = ref<Partial<Record<keyof NewVehicle, string>>>({})
const busy = ref(false)
const { addVehicle, vehicles } = useWorkspace()
const source = useDataSource()
const toast = useToast()
watch(open, (value) => {
  if (value) {
    name.value = ''
    plate.value = ''
    error.value = ''
    fields.value = {}
  }
})
function clearField(field: keyof NewVehicle) {
  fields.value = { ...fields.value, [field]: undefined }
  error.value = ''
}
async function submit() {
  if (busy.value) return
  if (!source.isPreview) {
    error.value = 'Vehicle creation is disabled for the current live contract.'
    return
  }
  error.value = ''
  fields.value = validateVehicleFields({ name: name.value, plate: plate.value }, vehicles.value)
  if (Object.keys(fields.value).length) return
  busy.value = true
  try {
    await addVehicle({ name: name.value, plate: plate.value })
    open.value = false
    toast.add({
      title: source.isPreview ? 'Vehicle added to your preview fleet' : 'Vehicle added',
      description: 'Tracker assignment is required before telemetry can appear.',
      icon: 'i-lucide-circle-check',
      color: 'success',
    })
  } catch (submitError) {
    if (isApiError(submitError) && submitError.fields.length) {
      fields.value = Object.fromEntries(
        submitError.fields.map((issue) => [
          issue.path.split('.').at(-1) ?? issue.path,
          issue.message,
        ]),
      ) as Partial<Record<keyof NewVehicle, string>>
    }
    error.value = errorMessage(submitError, 'Unable to add vehicle. Try again.')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <UModal
    v-model:open="open"
    title="A new addition to your fleet"
    :description="
      source.isPreview
        ? 'Add a vehicle to this preview session. No backend data will be changed.'
        : 'Vehicle creation is disabled in live mode until the backend contract is verified.'
    "
    ><template #body
      ><form id="add-vehicle" class="form-stack" novalidate @submit.prevent="submit">
        <UFormField label="Vehicle name" name="name" required :error="fields.name"
          ><UInput
            v-model="name"
            placeholder="e.g. Transit 09"
            class="w-full"
            maxlength="60"
            autofocus
            :aria-invalid="Boolean(fields.name)"
            @input="clearField('name')"
          /><template #error="{ error: fieldError }"
            ><span role="alert">{{ fieldError }}</span></template
          ></UFormField
        ><UFormField label="Registration" name="plate" required :error="fields.plate"
          ><UInput
            v-model="plate"
            placeholder="e.g. CA 8X42P9"
            class="w-full"
            maxlength="20"
            :aria-invalid="Boolean(fields.plate)"
            @input="clearField('plate')"
          /><template #error="{ error: fieldError }"
            ><span role="alert">{{ fieldError }}</span></template
          ></UFormField
        >
        <p class="form-hint">
          <UIcon name="i-lucide-info" />New vehicles start offline until a tracker is assigned.
        </p>
        <p v-if="error" role="alert" class="form-error">{{ error }}</p>
      </form></template
    ><template #footer
      ><div class="modal-footer">
        <UButton color="neutral" variant="outline" :disabled="busy" @click="open = false"
          >Cancel</UButton
        ><UButton type="submit" form="add-vehicle" :loading="busy" icon="i-lucide-plus"
          >Add vehicle</UButton
        >
      </div></template
    ></UModal
  >
</template>
