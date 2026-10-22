<script setup lang="ts">
import type { Vehicle } from '../domain/fleet'
defineProps<{ vehicles: Vehicle[]; compact?: boolean }>()
</script>

<template>
  <div class="table-scroll">
    <table class="data-table vehicle-table">
      <thead>
        <tr>
          <th>Vehicle</th>
          <th>Driver</th>
          <th>Status</th>
          <th>Location</th>
          <th>Fuel level</th>
          <th>Last updated</th>
          <th><span class="sr-only">Details</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="vehicle in vehicles" :key="vehicle.id">
          <td>
            <NuxtLink :to="`/vehicles/${vehicle.id}`" class="vehicle-cell"
              ><span class="vehicle-icon"
                ><UIcon
                  :name="
                    vehicle.type === 'Box truck' ? 'i-lucide-truck' : 'i-lucide-car-front'
                  " /></span
              ><span
                ><strong>{{ vehicle.name }}</strong
                ><small>{{ vehicle.plate }}</small></span
              ></NuxtLink
            >
          </td>
          <td>
            <div class="driver-cell">
              <span class="avatar" :class="`avatar-${vehicle.id.slice(-1)}`">{{
                vehicle.initials ?? '—'
              }}</span
              >{{ vehicle.driver ?? 'Not assigned' }}
            </div>
          </td>
          <td><UiStatusBadge :status="vehicle.status" /></td>
          <td>
            <span class="table-location"
              ><UIcon name="i-lucide-map-pin" />{{
                vehicle.location?.split(',')[0] ?? 'Not provided'
              }}</span
            >
          </td>
          <td>
            <div v-if="vehicle.fuel !== null" class="fuel-cell">
              <UIcon name="i-lucide-fuel" :class="{ 'text-warning': vehicle.fuel < 25 }" /><span
                class="fuel-track"
                ><span
                  :style="{ width: `${vehicle.fuel}%` }"
                  :class="{ low: vehicle.fuel < 25 }" /></span
              ><span>{{ vehicle.fuel }}%</span>
            </div>
            <span v-else class="muted">Unavailable</span>
          </td>
          <td class="muted table-time">{{ vehicle.updated ?? 'No telemetry' }}</td>
          <td>
            <NuxtLink
              :to="`/vehicles/${vehicle.id}`"
              class="row-arrow"
              :aria-label="`View ${vehicle.name}`"
              ><UIcon name="i-lucide-chevron-right"
            /></NuxtLink>
          </td>
        </tr>
      </tbody>
    </table>
    <UiEmptyState
      v-if="!vehicles.length"
      title="No vehicles match"
      description="Try a different search or clear your filters."
    />
  </div>
</template>
