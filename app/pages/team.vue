<script setup lang="ts">
const search = ref('')
const members = [
  {
    name: 'Alex Carter',
    initials: 'AC',
    email: 'alex@example.com',
    role: 'TENANT_ADMIN',
    label: 'Organization administrator',
    last: 'Preview account',
  },
  {
    name: 'Jordan Lee',
    initials: 'JL',
    email: 'jordan@example.com',
    role: 'OPERATOR',
    label: 'Fleet operator',
    last: 'Sample member',
  },
  {
    name: 'Sam Rivera',
    initials: 'SR',
    email: 'sam@example.com',
    role: 'VIEWER',
    label: 'Read-only viewer',
    last: 'Sample member',
  },
]
const visible = computed(() =>
  members.filter((m) =>
    `${m.name} ${m.email} ${m.role}`.toLowerCase().includes(search.value.toLowerCase()),
  ),
)
</script>

<template>
  <div>
    <UiPageHeading
      title="Good people. Clear access."
      description="The right visibility for everyone on your team."
      eyebrow="TEAM & ACCESS"
      ><UButton to="/admin/roles" color="neutral" variant="outline" icon="i-lucide-shield-check"
        >Explore role templates</UButton
      ></UiPageHeading
    >
    <div class="info-banner warning-banner">
      <UIcon name="i-lucide-shield-alert" />
      <p>
        Sample members only. Invitations and role changes are unavailable until effective backend
        permissions and tenant boundaries are enforced.
      </p>
    </div>
    <section class="panel">
      <div class="toolbar">
        <UInput
          v-model="search"
          icon="i-lucide-search"
          placeholder="Search team members..."
          aria-label="Search team members"
        /><span class="record-count">{{ visible.length }} preview members</span>
      </div>
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>Team member</th>
              <th>Role</th>
              <th>Access scope</th>
              <th>Status</th>
              <th>Account</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="member in visible" :key="member.email">
              <td>
                <div class="vehicle-cell">
                  <span class="avatar">{{ member.initials }}</span
                  ><span
                    ><strong>{{ member.name }}</strong
                    ><small>{{ member.email }}</small></span
                  >
                </div>
              </td>
              <td>
                <strong class="font-medium">{{ member.label }}</strong>
                <p class="muted text-[9px] mt-1 font-mono">{{ member.role }}</p>
              </td>
              <td>Northstar Logistics</td>
              <td><UiStatusBadge status="active" /></td>
              <td class="muted">{{ member.last }}</td>
            </tr>
          </tbody>
        </table>
        <UiEmptyState
          v-if="!visible.length"
          title="No team members found"
          description="Try another name or role."
        />
      </div>
    </section>
  </div>
</template>
