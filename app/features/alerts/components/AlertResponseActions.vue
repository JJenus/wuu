<script setup lang="ts">
import { useAlertResponse } from '../composables/useAlertResponse'
import type { AlertState } from '../../workspace/domain/types'

const props = defineProps<{ id: string; status: AlertState; detail?: boolean }>()
const { source, allowed, accessPending, accessError, busy, failure, success, submit, retryAccess } =
  useAlertResponse()
const resolving = ref(false)
const notes = ref('')
const notesValid = computed(
  () => notes.value.trim().length >= 10 && notes.value.trim().length <= 1000,
)
const suffix = computed(() => (source.isPreview && props.detail ? ' in preview' : ''))
watch(
  () => props.id,
  () => {
    resolving.value = false
    notes.value = ''
    failure.value = ''
    success.value = ''
  },
)
async function resolveAlert() {
  if (source.isPreview) {
    await submit(props.id, 'resolved')
    return
  }
  resolving.value = true
}
async function confirmResolution() {
  if (!notesValid.value || busy.value || !allowed.value) return
  if (await submit(props.id, 'resolved', notes.value.trim())) {
    resolving.value = false
    notes.value = ''
  }
}
function cancelResolution() {
  resolving.value = false
  notes.value = ''
  failure.value = ''
}
</script>

<template>
  <div class="space-y-3">
    <p v-if="accessPending" class="muted text-sm" role="status">Checking alert permissions…</p>
    <div v-else-if="accessError" class="space-y-2">
      <p role="alert" class="form-error">Alert permissions could not be checked.</p>
      <UButton color="neutral" variant="outline" size="sm" @click="retryAccess"
        >Retry permissions</UButton
      >
    </div>
    <p v-else-if="!allowed" class="muted text-sm">You do not have permission to change alerts.</p>
    <p v-if="failure" role="alert" class="form-error">{{ failure }}</p>
    <p v-if="success" role="status" class="text-sm text-default">{{ success }}</p>
    <template v-if="allowed">
      <div class="flex flex-wrap gap-2">
        <UButton
          v-if="status === 'active'"
          color="neutral"
          variant="outline"
          size="sm"
          :loading="busy"
          :disabled="busy || resolving"
          @click="submit(id, 'acknowledged')"
          >Acknowledge{{ suffix }}</UButton
        >
        <UButton
          v-if="status !== 'resolved' && !resolving"
          size="sm"
          icon="i-lucide-check"
          :loading="busy"
          :disabled="busy"
          @click="resolveAlert"
          >Resolve{{ suffix }}</UButton
        >
      </div>
      <form
        v-if="resolving && status !== 'resolved'"
        class="space-y-3"
        @submit.prevent="confirmResolution"
      >
        <UFormField
          label="Resolution notes"
          :for="`resolution-notes-${id}`"
          hint="10–1000 characters. Review before confirming."
        >
          <UTextarea
            :id="`resolution-notes-${id}`"
            v-model="notes"
            class="w-full"
            :disabled="busy"
            :maxlength="1000"
            :rows="3"
            required
          />
        </UFormField>
        <p class="muted text-sm">
          This records your authenticated identity and notes. It does not send a notification or
          device command.
        </p>
        <div class="flex flex-wrap gap-2">
          <UButton type="submit" :disabled="busy || !notesValid" :loading="busy"
            >Confirm resolution</UButton
          >
          <UButton color="neutral" variant="outline" :disabled="busy" @click="cancelResolution"
            >Cancel</UButton
          >
        </div>
      </form>
    </template>
  </div>
</template>
