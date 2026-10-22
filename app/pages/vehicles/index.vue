<script setup lang="ts">
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import { filterVehicles } from '~/features/fleet/domain/fleet'
import VehicleTable from '~/features/fleet/components/VehicleTable.vue'
import AddVehicleModal from '~/features/fleet/components/AddVehicleModal.vue'
const { vehicles } = useWorkspace()
const search = ref('')
const status = ref('all')
const addOpen = ref(false)
function clearFilters() {
  search.value = ''
  status.value = 'all'
}
const filtered = computed(() => filterVehicles(vehicles.value, search.value, status.value))
useHead({ title: 'Vehicles — Wuu' })
</script>

<template>
  <div>
    <UiPageHeading
      title="A place for every vehicle."
      description="Get to know your fleet, down to the last detail."
      eyebrow="YOUR FLEET"
      ><UButton icon="i-lucide-plus" @click="addOpen = true">Add vehicle</UButton></UiPageHeading
    >
    <section class="panel">
      <div class="toolbar">
        <div class="toolbar-left">
          <UInput
            v-model="search"
            icon="i-lucide-search"
            placeholder="Search vehicles or drivers..."
            aria-label="Search vehicles"
          /><select v-model="status" class="native-select" aria-label="Vehicle status">
            <option value="all">All statuses</option>
            <option value="moving">Moving</option>
            <option value="idle">Idle</option>
            <option value="parked">Parked</option>
            <option value="offline">Offline</option>
          </select>
        </div>
        <span class="record-count">{{ filtered.length }} vehicles · Preview fleet</span>
      </div>
      <VehicleTable :vehicles="filtered" />
      <div class="table-footer">
        <span>{{ filtered.length }} of {{ vehicles.length }} vehicles</span
        ><UButton
          v-if="search || status !== 'all'"
          color="neutral"
          variant="link"
          size="xs"
          @click="clearFilters"
          >Clear filters</UButton
        >
      </div>
    </section>
    <AddVehicleModal v-model:open="addOpen" />
  </div>
</template>
