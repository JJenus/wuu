<script setup lang="ts">
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
const open = defineModel<boolean>('open', { default: false })
const name = ref('')
const plate = ref('')
const error = ref('')
const busy = ref(false)
const { addVehicle } = useWorkspace()
const toast = useToast()
watch(open, (value) => {
  if (value) {
    name.value = ''
    plate.value = ''
    error.value = ''
  }
})
async function submit() {
  busy.value = true
  error.value = ''
  try {
    await addVehicle({ name: name.value, plate: plate.value })
    open.value = false
    toast.add({
      title: 'Vehicle added to your preview fleet',
      description: 'Tracker assignment is required before telemetry can appear.',
      icon: 'i-lucide-circle-check',
      color: 'success',
    })
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Unable to add vehicle. Try again.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <UModal
    v-model:open="open"
    title="A new addition to your fleet"
    description="Add a vehicle to this preview session. No backend data will be changed."
    ><template #body
      ><form id="add-vehicle" class="form-stack" @submit.prevent="submit">
        <UFormField label="Vehicle name" name="name" required
          ><UInput
            v-model="name"
            placeholder="e.g. Transit 09"
            class="w-full"
            maxlength="60"
            autofocus /></UFormField
        ><UFormField label="Registration" name="plate" required
          ><UInput v-model="plate" placeholder="e.g. CA 8X42P9" class="w-full" maxlength="20"
        /></UFormField>
        <p class="form-hint">
          <UIcon name="i-lucide-info" />New vehicles start offline until a tracker is assigned.
        </p>
        <p v-if="error" role="alert" class="form-error">{{ error }}</p>
      </form></template
    ><template #footer
      ><div class="modal-footer">
        <UButton color="neutral" variant="outline" @click="open = false">Cancel</UButton
        ><UButton type="submit" form="add-vehicle" :loading="busy" icon="i-lucide-plus"
          >Add vehicle</UButton
        >
      </div></template
    ></UModal
  >
</template>
