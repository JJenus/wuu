<script setup lang="ts">
const ready = ref(false)
onMounted(() => {
  ready.value = true
})
</script>

<template>
  <UApp :toaster="{ position: 'bottom-right' }">
    <div :inert="!ready" :aria-busy="!ready" :data-workspace-ready="ready">
      <NuxtLayout><NuxtPage /></NuxtLayout>
    </div>
  </UApp>
</template>
