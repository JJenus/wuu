<script setup lang="ts">
import { roleTemplates } from '~/policies/access'
const futureRoles = [
  {
    scope: 'Platform',
    roles:
      'Support agent, security administrator, device operations technician, finance administrator, read-only auditor',
  },
  {
    scope: 'Business',
    roles:
      'Organization owner, fleet manager, dispatcher, device installer, billing manager, asset-scoped viewer',
  },
  {
    scope: 'Ride-hailing (future)',
    roles: 'Rider, driver, dispatch agent, compliance reviewer, payout operator',
  },
]
</script>

<template>
  <div>
    <UiPageHeading
      title="Clear roles. Better boundaries."
      description="Give people the visibility they need, and nothing they don’t."
      eyebrow="ROLES & PERMISSIONS"
    />
    <div class="info-banner warning-banner">
      <UIcon name="i-lucide-shield-alert" />
      <p>
        These are presentation templates, not the signed-in user's effective permissions. Backend
        authorization is authoritative. Role editing stays unavailable until a verified role catalog
        and effective-permissions workflow are integrated.
      </p>
    </div>
    <div class="content-grid">
      <article v-for="template in roleTemplates" :key="template.role" class="panel role-card">
        <span class="role-icon"
          ><UIcon
            :name="
              template.role === 'SUPER_ADMIN'
                ? 'i-lucide-shield-check'
                : template.role === 'VIEWER'
                  ? 'i-lucide-eye'
                  : template.role === 'OPERATOR'
                    ? 'i-lucide-radio'
                    : 'i-lucide-users'
            "
        /></span>
        <h3>{{ template.label }}</h3>
        <small>{{ template.role }} · {{ template.scope }} scope</small>
        <div class="permission-list">
          <span v-for="permission in template.permissions" :key="permission">{{ permission }}</span>
        </div>
      </article>
    </div>
    <section class="panel mt-6">
      <div class="panel-heading">
        <div>
          <h2>Designed for what comes next.</h2>
          <p>Proposed responsibilities, not implemented roles.</p>
        </div>
        <span class="subtle-badge">Future scope</span>
      </div>
      <div class="section-body !pt-0">
        <div v-for="group in futureRoles" :key="group.scope" class="settings-row">
          <div>
            <h3>{{ group.scope }}</h3>
            <p>{{ group.roles }}</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
