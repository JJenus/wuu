<script setup lang="ts">
import type { MapSearchService, MapSearchResult } from '../domain/types'
import { useMapSearch } from '../composables/useMapSearch'
const props = withDefaults(defineProps<{ service: MapSearchService; label?: string }>(), {
  label: 'Search map',
})
const emit = defineEmits<{ select: [MapSearchResult] }>()
const { query, results, busy, error, open, search, close, clear } = useMapSearch(
  () => props.service,
)
const root = ref<HTMLElement>()
let suppressFocus = false
function focusSearch() {
  if (!suppressFocus && query.value.trim()) {
    open.value = true
    search()
  }
}
function choose(result: MapSearchResult) {
  close()
  emit('select', result)
  suppressFocus = true
  root.value?.querySelector('input')?.focus()
  suppressFocus = false
}
function key(event: KeyboardEvent) {
  if (event.key === 'Enter' && event.target instanceof HTMLInputElement) {
    event.preventDefault()
    if (!busy.value && results.value[0]) choose(results.value[0])
  }
  if (event.key === 'Escape') {
    close()
    event.stopPropagation()
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    const options = Array.from(
      root.value?.querySelectorAll<HTMLButtonElement>('[data-search-result]') ?? [],
    )
    if (!options.length) return
    event.preventDefault()
    const index = options.indexOf(document.activeElement as HTMLButtonElement)
    options[
      (index + (event.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length
    ]?.focus()
  }
}
</script>
<template>
  <div
    ref="root"
    class="map-search"
    @keydown="key"
    @focusout="
      (event) => {
        if (!root?.contains(event.relatedTarget as Node)) close()
      }
    "
  >
    <div class="map-search-field">
      <UIcon name="i-lucide-search" /><input
        v-model="query"
        type="search"
        :aria-label="label"
        placeholder="Find a vehicle or sample place…"
        maxlength="120"
        @focus="focusSearch"
      /><button v-if="query" type="button" aria-label="Clear map search" @click="clear">
        <UIcon name="i-lucide-x" />
      </button>
    </div>
    <div v-if="open" class="map-search-results" role="dialog" :aria-label="`${label} results`">
      <p v-if="busy" class="map-search-message" role="status">Searching map…</p>
      <div v-else-if="error" class="map-search-message" role="alert">
        <p>{{ error }}</p>
        <UButton type="button" size="sm" variant="outline" color="neutral" @click="search"
          >Retry map search</UButton
        >
      </div>
      <template v-else
        ><p v-if="!results.length" class="map-search-message" role="status">
          No map results found.
        </p>
        <button
          v-for="result in results"
          :key="result.id"
          type="button"
          data-search-result
          :aria-label="`${result.label} · ${result.kind === 'vehicle' ? 'Vehicle' : 'Place'}`"
          @click="choose(result)"
        >
          <UIcon :name="result.kind === 'vehicle' ? 'i-lucide-truck' : 'i-lucide-map-pin'" /><span
            ><strong>{{ result.label }}</strong
            ><small>{{ result.description }}</small></span
          ><span class="preview-tag">{{ result.source }}</span>
        </button></template
      >
    </div>
  </div>
</template>
