<script setup lang="ts">
useHead({ title: 'Access unavailable — Wuu' })
</script>
<template>
  <UiEmptyState
    title="Platform access is unavailable"
    description="Platform administration needs a verified effective-permissions contract. Workspace access does not grant platform access."
    icon="i-lucide-shield-alert"
    ><UButton to="/">Back to workspace</UButton></UiEmptyState
  >
</template>
