<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  icon: string
  detail: string
  note: string
  trend?: string
  featured?: boolean
  chart: string
}>()
</script>

<template>
  <article class="metric-card" :class="{ featured }">
    <div class="metric-top">
      <span>{{ label }}</span
      ><span class="metric-icon"><UIcon :name="icon" /></span>
    </div>
    <div class="metric-middle">
      <strong>{{ value }}</strong
      ><svg viewBox="0 0 100 40" class="sparkline" aria-hidden="true">
        <path :d="`${chart} L100 40 L0 40 Z`" fill="currentColor" opacity=".06" />
        <path
          :d="chart"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </div>
    <div class="metric-bottom">
      <span class="metric-trend" :class="trend"
        ><UIcon v-if="trend === 'positive'" name="i-lucide-arrow-up-right" />{{ detail }}</span
      ><span>{{ note }}</span>
    </div>
  </article>
</template>
