<script setup lang="ts">
import type { NuxtError } from '#app'
defineProps<{ error: NuxtError }>()
</script>
<template>
  <UApp
    ><main class="min-h-screen flex items-center justify-center p-6">
      <div class="panel max-w-md w-full">
        <UiEmptyState
          :title="error.statusCode === 404 ? 'A little off the route.' : 'A bump in the road.'"
          :description="
            error.statusCode === 404
              ? 'We couldn’t find this page. Let’s get you back to your fleet.'
              : 'Something went wrong. Please try returning to your workspace.'
          "
          icon="i-lucide-route"
          ><UButton icon="i-lucide-arrow-left" @click="clearError({ redirect: '/' })"
            >Back to workspace</UButton
          ></UiEmptyState
        >
      </div>
    </main></UApp
  >
</template>
