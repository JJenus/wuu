<script setup lang="ts">
const search = ref('')
const selected = ref<string | null>(null)
const open = ref(false)
const organizations = [
  {
    name: 'Northstar Logistics',
    initials: 'NL',
    slug: 'northstar-logistics',
    vehicles: 8,
    members: 3,
    plan: 'Fleet workspace',
  },
  {
    name: 'Coastal Deliveries',
    initials: 'CD',
    slug: 'coastal-deliveries',
    vehicles: 12,
    members: 5,
    plan: 'Illustrative organization',
  },
  {
    name: 'Evergreen Transport',
    initials: 'ET',
    slug: 'evergreen-transport',
    vehicles: 6,
    members: 2,
    plan: 'Illustrative organization',
  },
]
const visible = computed(() =>
  organizations.filter((o) => o.name.toLowerCase().includes(search.value.toLowerCase())),
)
const detail = computed(() => organizations.find((o) => o.slug === selected.value))
function show(slug: string) {
  selected.value = slug
  open.value = true
}
</script>

<template>
  <div>
    <UiPageHeading
      title="Room for every business."
      description="A clear view of the organizations that call Wayline home."
      eyebrow="ORGANIZATIONS"
      ><span class="subtle-badge">Illustrative data only</span></UiPageHeading
    >
    <div class="info-banner">
      <UIcon name="i-lucide-building-2" />
      <p>
        Organization APIs exist, but this screen is not connected. Creation, suspension, and
        cross-tenant access stay unavailable pending server-side authorization verification.
      </p>
    </div>
    <section class="panel">
      <div class="toolbar">
        <UInput
          v-model="search"
          icon="i-lucide-search"
          placeholder="Search organizations..."
          aria-label="Search organizations"
        /><span class="record-count">{{ visible.length }} illustrative organizations</span>
      </div>
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Organization</th>
              <th>Vehicles</th>
              <th>Members</th>
              <th>Workspace</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            <tr v-for="org in visible" :key="org.slug">
              <td>
                <button class="vehicle-cell bg-transparent text-left" @click="show(org.slug)">
                  <span class="avatar">{{ org.initials }}</span
                  ><span
                    ><strong>{{ org.name }}</strong
                    ><small>{{ org.slug }}</small></span
                  >
                </button>
              </td>
              <td>{{ org.vehicles }}</td>
              <td>{{ org.members }}</td>
              <td>{{ org.plan }}</td>
              <td><UiStatusBadge status="active" /></td>
              <td>
                <UButton
                  icon="i-lucide-arrow-up-right"
                  color="neutral"
                  variant="ghost"
                  :aria-label="`View ${org.name}`"
                  @click="show(org.slug)"
                />
              </td>
            </tr>
          </tbody>
        </table>
        <UiEmptyState
          v-if="!visible.length"
          title="No organizations found"
          description="Try a different organization name."
        />
      </div>
    </section>
    <UModal
      v-model:open="open"
      :title="detail?.name || 'Organization'"
      description="Illustrative record. This does not switch tenant context."
      ><template #body
        ><dl v-if="detail" class="detail-info">
          <div>
            <dt>Slug</dt>
            <dd>{{ detail.slug }}</dd>
          </div>
          <div>
            <dt>Workspace</dt>
            <dd>{{ detail.plan }}</dd>
          </div>
          <div>
            <dt>Sample vehicles</dt>
            <dd>{{ detail.vehicles }}</dd>
          </div>
          <div>
            <dt>Sample members</dt>
            <dd>{{ detail.members }}</dd>
          </div>
        </dl></template
      ></UModal
    >
  </div>
</template>
