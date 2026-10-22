<script setup lang="ts">
import { errorMessage } from '~/features/core/domain/errors'

const props = withDefaults(
  defineProps<{
    pending?: boolean
    error?: unknown
    empty?: boolean
    loadingLabel?: string
    errorTitle?: string
    emptyTitle?: string
    emptyDescription?: string
  }>(),
  {
    pending: false,
    empty: false,
    loadingLabel: 'Loading your workspace…',
    errorTitle: 'This view could not load',
    emptyTitle: 'Nothing here yet',
    emptyDescription: 'Records will appear here when they become available.',
  },
)
const emit = defineEmits<{ retry: [] }>()
const message = computed(() => (props.error ? errorMessage(props.error) : ''))
const requestId = computed(() => {
  if (!props.error || typeof props.error !== 'object' || !('requestId' in props.error)) return null
  const value = props.error.requestId
  return typeof value === 'string' ? value : null
})
</script>

<template>
  <div v-if="error" class="async-state async-state-error" role="alert" data-testid="async-error">
    <span class="async-state-icon"><UIcon name="i-lucide-cloud-off" /></span>
    <h3>{{ errorTitle }}</h3>
    <p>{{ message }}</p>
    <p v-if="requestId" class="async-request-id">Reference: {{ requestId }}</p>
    <UButton icon="i-lucide-refresh-cw" color="neutral" variant="outline" @click="emit('retry')"
      >Try again</UButton
    >
  </div>
  <div v-else-if="pending" class="async-state" role="status" aria-live="polite">
    <span class="async-spinner" aria-hidden="true" />
    <p>{{ loadingLabel }}</p>
  </div>
  <div v-else-if="empty" class="async-state">
    <span class="async-state-icon"><UIcon name="i-lucide-inbox" /></span>
    <h3>{{ emptyTitle }}</h3>
    <p>{{ emptyDescription }}</p>
    <slot name="empty" />
  </div>
  <slot v-else />
</template>
