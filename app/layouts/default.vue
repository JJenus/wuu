<script setup lang="ts">
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
const route = useRoute()
const mobileOpen = ref(false)
const isMobile = ref(false)
let mobileQuery: MediaQueryList | undefined
function updateViewport() {
  isMobile.value = mobileQuery?.matches ?? false
}
const searchOpen = ref(false)
const search = ref('')
const notificationsOpen = ref(false)
const { vehicles, alerts } = useWorkspace()
const results = computed(() =>
  vehicles.value
    .filter((v) =>
      `${v.name} ${v.plate} ${v.driver}`.toLowerCase().includes(search.value.toLowerCase()),
    )
    .slice(0, 5),
)
const activeAlerts = computed(() => alerts.value.filter((a) => a.status === 'active'))
const admin = computed(() => route.path.startsWith('/admin'))
function isActive(path: string) {
  return (
    route.path === path || (path !== '/' && path !== '/admin' && route.path.startsWith(`${path}/`))
  )
}
const detailLabel = computed(() => {
  if (route.path.startsWith('/alerts/')) return 'Alert details'
  if (route.path.startsWith('/trips/')) return 'Trip details'
  if (route.path.startsWith('/geofences/')) return 'Geofence details'
  if (route.path.startsWith('/vehicles/')) return 'Vehicle details'
  return 'Workspace'
})
const navigation = [
  { label: 'Overview', icon: 'i-lucide-layout-dashboard', to: '/' },
  { label: 'Live map', icon: 'i-lucide-map', to: '/map' },
  { label: 'Vehicles', icon: 'i-lucide-truck', to: '/vehicles' },
  { label: 'Trips', icon: 'i-lucide-route', to: '/trips' },
  { label: 'Alerts', icon: 'i-lucide-bell-ring', to: '/alerts' },
  { label: 'Geofences', icon: 'i-lucide-scan', to: '/geofences' },
]
const management = [
  { label: 'Team & access', icon: 'i-lucide-users', to: '/team' },
  { label: 'Settings', icon: 'i-lucide-settings-2', to: '/settings' },
]
const adminNavigation = [
  { label: 'Platform overview', icon: 'i-lucide-layout-dashboard', to: '/admin' },
  { label: 'Organizations', icon: 'i-lucide-building-2', to: '/admin/organizations' },
  { label: 'Roles & permissions', icon: 'i-lucide-shield-check', to: '/admin/roles' },
]
watch(
  () => route.path,
  () => {
    mobileOpen.value = false
    searchOpen.value = false
  },
)
function keyboard(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
    event.preventDefault()
    searchOpen.value = !searchOpen.value
  }
  if (event.key === 'Escape') mobileOpen.value = false
}
onMounted(() => {
  document.addEventListener('keydown', keyboard)
  mobileQuery = window.matchMedia('(max-width: 760px)')
  mobileQuery.addEventListener('change', updateViewport)
  updateViewport()
})
onUnmounted(() => {
  document.removeEventListener('keydown', keyboard)
  mobileQuery?.removeEventListener('change', updateViewport)
})
watch(mobileOpen, async (open) => {
  if (!isMobile.value) return
  await nextTick()
  if (open) document.querySelector<HTMLElement>('#workspace-navigation a')?.focus()
  else document.querySelector<HTMLElement>('#mobile-navigation-trigger')?.focus()
})
</script>

<template>
  <div class="workspace-shell">
    <a class="skip-link" href="#main-content">Skip to content</a>
    <button
      v-if="mobileOpen"
      class="sidebar-scrim"
      aria-label="Close navigation"
      tabindex="-1"
      @click="mobileOpen = false"
    />
    <aside
      id="workspace-navigation"
      class="sidebar"
      :class="{ 'is-open': mobileOpen }"
      :inert="isMobile && !mobileOpen"
    >
      <NuxtLink to="/" class="brand" aria-label="Wayline home"
        ><span class="brand-mark"
          ><svg viewBox="0 0 32 32" fill="none">
            <path
              d="m5 9 5 15 6-12 6 12 5-15"
              stroke="currentColor"
              stroke-width="2.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            /></svg></span
        >wayline<span class="brand-period">.</span></NuxtLink
      >
      <UPopover :content="{ align: 'start' }">
        <button class="organization-switch">
          <span class="org-icon"
            ><UIcon :name="admin ? 'i-lucide-shield-check' : 'i-lucide-building-2'" /></span
          ><span
            ><strong>{{ admin ? 'Wayline platform' : 'Northstar Logistics' }}</strong
            ><small>{{ admin ? 'Administration preview' : 'Business workspace' }}</small></span
          ><UIcon name="i-lucide-chevrons-up-down" class="org-chevron" />
        </button>
        <template #content
          ><div class="workspace-switch-menu">
            <span class="eyebrow">Preview workspaces</span
            ><NuxtLink to="/"><UIcon name="i-lucide-building-2" />Northstar Logistics</NuxtLink
            ><NuxtLink to="/admin"
              ><UIcon name="i-lucide-shield-check" />Platform administration</NuxtLink
            >
            <p>Preview navigation only. This is not an authorization boundary.</p>
          </div></template
        >
      </UPopover>
      <div class="nav-section-label">{{ admin ? 'PLATFORM' : 'WORKSPACE' }}</div>
      <nav aria-label="Main navigation">
        <NuxtLink
          v-for="item in admin ? adminNavigation : navigation"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: isActive(item.to) }"
          :aria-current="
            isActive(item.to) ? (route.path === item.to ? 'page' : 'location') : undefined
          "
          ><UIcon :name="item.icon" /><span>{{ item.label }}</span
          ><span v-if="item.to === '/alerts' && activeAlerts.length" class="nav-count">{{
            activeAlerts.length
          }}</span></NuxtLink
        >
      </nav>
      <div class="nav-section-label management-label">MANAGE</div>
      <nav aria-label="Workspace management">
        <NuxtLink
          v-for="item in management"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: route.path === item.to }"
          ><UIcon :name="item.icon" />{{ item.label }}</NuxtLink
        >
      </nav>
      <div class="sidebar-bottom">
        <div class="preview-card">
          <span class="preview-orbit"><UIcon name="i-lucide-sparkles" /></span
          ><strong>A little clarity goes a long way.</strong>
          <p>Explore your fleet workspace with safe, sample data.</p>
          <span class="preview-tag"><span />Interactive preview</span>
        </div>
        <NuxtLink :to="admin ? '/' : '/admin'" class="nav-item platform-link"
          ><UIcon :name="admin ? 'i-lucide-arrow-left' : 'i-lucide-shield-check'" />{{
            admin ? 'Business workspace' : 'Platform preview'
          }}<UIcon name="i-lucide-arrow-up-right"
        /></NuxtLink>
        <div class="sidebar-user">
          <span class="avatar user-avatar">AC</span
          ><span><strong>Alex Carter</strong><small>Workspace preview</small></span
          ><NuxtLink to="/settings" aria-label="Account settings"
            ><UIcon name="i-lucide-settings"
          /></NuxtLink>
        </div>
      </div>
    </aside>
    <div class="workspace-body" :inert="isMobile && mobileOpen">
      <header class="topbar">
        <div class="breadcrumbs">
          <UButton
            id="mobile-navigation-trigger"
            class="mobile-menu"
            icon="i-lucide-menu"
            color="neutral"
            variant="ghost"
            aria-label="Open navigation"
            aria-controls="workspace-navigation"
            :aria-expanded="mobileOpen"
            @click="mobileOpen = true"
          /><UIcon :name="admin ? 'i-lucide-shield-check' : 'i-lucide-house'" /><span>{{
            admin ? 'Platform' : 'Workspace'
          }}</span
          ><UIcon name="i-lucide-chevron-right" /><strong>{{
            [...navigation, ...management, ...adminNavigation].find((n) => n.to === route.path)
              ?.label || detailLabel
          }}</strong>
        </div>
        <div class="topbar-actions">
          <button class="search-trigger" @click="searchOpen = true">
            <UIcon name="i-lucide-search" /><span>Search anything...</span><kbd>⌘ K</kbd></button
          ><span class="topbar-divider" /><UPopover
            v-model:open="notificationsOpen"
            :content="{ align: 'end' }"
            ><button class="notification-button" aria-label="Open notifications">
              <UIcon name="i-lucide-bell" /><span v-if="activeAlerts.length" /></button
            ><template #content
              ><div class="notification-popover">
                <div class="panel-heading">
                  <h3>Notifications</h3>
                  <span class="preview-tag">Sample data</span>
                </div>
                <NuxtLink
                  v-for="alert in activeAlerts"
                  :key="alert.id"
                  to="/alerts"
                  @click="notificationsOpen = false"
                  ><span class="alert-icon" :class="alert.severity"
                    ><UIcon :name="alert.icon" /></span
                  ><span
                    ><strong>{{ alert.title }}</strong
                    ><small>{{ alert.vehicle }} · {{ alert.time }}</small></span
                  ></NuxtLink
                >
                <p v-if="!activeAlerts.length" class="muted">You're all caught up.</p>
              </div></template
            ></UPopover
          ><UColorModeButton color="neutral" variant="ghost" class="theme-toggle" /><span
            class="avatar topbar-avatar"
            >AC</span
          >
        </div>
      </header>
      <main id="main-content" class="main-content"><slot /></main>
      <footer class="workspace-footer">
        <span><span class="footer-dot" />Preview environment · No live vehicles connected</span
        ><span>Made for the road ahead.<span class="footer-brand">wayline.</span></span>
      </footer>
    </div>
    <UModal
      v-model:open="searchOpen"
      title="Find your way"
      description="Search vehicles, registrations, or drivers in the preview fleet."
      ><template #body
        ><UInput
          v-model="search"
          icon="i-lucide-search"
          placeholder="Search vehicles or drivers..."
          autofocus
          class="w-full"
          size="xl" />
        <div class="search-results">
          <NuxtLink v-for="vehicle in results" :key="vehicle.id" :to="`/vehicles/${vehicle.id}`"
            ><span class="vehicle-icon"><UIcon name="i-lucide-truck" /></span
            ><span
              ><strong>{{ vehicle.name }}</strong
              ><small>{{ vehicle.plate }} · {{ vehicle.driver }}</small></span
            ><UIcon name="i-lucide-arrow-up-right" /></NuxtLink
          ><UiEmptyState
            v-if="!results.length"
            title="No vehicles found"
            description="Try a different name, registration, or driver."
          /></div></template
    ></UModal>
  </div>
</template>
