<script setup lang="ts">
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import FleetMap from '~/features/fleet/components/FleetMap.vue'
const { vehicles, summary } = useWorkspace()
const status = ref('all')
const visible = computed(() =>
  vehicles.value.filter((v) => status.value === 'all' || v.status === status.value),
)
useHead({ title: 'Fleet map — Wayline' })
</script>

<template>
  <div>
    <UiPageHeading
      title="The whole picture."
      description="Find a vehicle, check its status, and see where the day takes it."
      eyebrow="FLEET MAP"
      ><select v-model="status" class="native-select" aria-label="Map vehicle status">
        <option value="all">All {{ summary.total }} vehicles</option>
        <option value="moving">Moving</option>
        <option value="idle">Idle</option>
        <option value="parked">Parked</option>
        <option value="offline">Offline</option>
      </select></UiPageHeading
    >
    <div class="info-banner">
      <UIcon name="i-lucide-info" />
      <p>
        This is an interactive, illustrated preview map—not a live GPS feed. Select a vehicle marker
        to see its details. Map zoom and status filters work locally.
      </p>
    </div>
    <section class="panel"><FleetMap :vehicles="visible" full /></section>
  </div>
</template>
