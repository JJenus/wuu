<script setup lang="ts">
defineProps<{ title: string; description: string; icon?: string }>()
</script>
<template>
  <div class="empty-state">
    <UIcon :name="icon || 'i-lucide-search'" />
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <slot />
  </div>
</template>
