<script setup lang="ts">
import MetricCard from '~/features/workspace/components/MetricCard.vue'
useHead({ title: 'Platform preview — Wuu' })
const capabilities = [
  {
    name: 'Organizations',
    detail: 'Organization CRUD controllers exist; integration is unverified.',
    status: 'API exists',
    link: '/admin/organizations',
    icon: 'i-lucide-building-2',
  },
  {
    name: 'Roles & permissions',
    detail: 'Four role templates found. Role-management endpoints are missing.',
    status: 'Backend gap',
    link: '/admin/roles',
    icon: 'i-lucide-shield-check',
  },
  {
    name: 'Vehicle operations',
    detail: 'Vehicle and tracker endpoints exist. Tenant isolation needs verification.',
    status: 'API exists',
    link: '/vehicles',
    icon: 'i-lucide-truck',
  },
  {
    name: 'Billing & ride-hailing',
    detail: 'Design documents only. No controllers found; excluded from this release.',
    status: 'Future scope',
    icon: 'i-lucide-route',
  },
]
</script>

<template>
  <div>
    <UiPageHeading
      title="A wider perspective."
      description="A thoughtful starting point for platform operations."
      eyebrow="PLATFORM ADMINISTRATION"
      ><UButton to="/" color="neutral" variant="outline" icon="i-lucide-arrow-left"
        >Business workspace</UButton
      ></UiPageHeading
    >
    <div class="info-banner warning-banner">
      <UIcon name="i-lucide-shield-alert" />
      <p>
        <strong>Administration concept preview.</strong> This route has no production access control
        and exposes only sample information. Backend permission enforcement is a release blocker—not
        something navigation guards can solve.
      </p>
    </div>
    <section class="metric-grid">
      <MetricCard
        label="Sample organizations"
        value="3"
        icon="i-lucide-building-2"
        detail="Concept data"
        note="not connected"
        chart="M0 30L30 30L45 20L70 20L100 8"
        featured
      /><MetricCard
        label="Verified role templates"
        value="4"
        icon="i-lucide-shield-check"
        detail="Found in code"
        note="not enforced here"
        chart="M0 25L25 25L25 20L75 20L75 10L100 10"
      /><MetricCard
        label="Live integrations"
        value="0"
        icon="i-lucide-unplug"
        detail="Preview isolated"
        note="by design"
        chart="M0 30H100"
      /><MetricCard
        label="Security release gates"
        value="4"
        icon="i-lucide-lock-keyhole"
        detail="Must be verified"
        note="before release"
        chart="M0 10L25 15L50 15L75 22L100 22"
      />
    </section>
    <section class="panel">
      <div class="panel-heading">
        <div>
          <h2>Build on what’s real.</h2>
          <p>Capabilities grounded in the backend—not assumptions.</p>
        </div>
      </div>
      <div class="section-body !pt-0">
        <div v-for="cap in capabilities" :key="cap.name" class="settings-row">
          <div class="flex items-center gap-3">
            <span class="vehicle-icon"><UIcon :name="cap.icon" /></span>
            <div>
              <h3>{{ cap.name }}</h3>
              <p>{{ cap.detail }}</p>
            </div>
          </div>
          <NuxtLink v-if="cap.link" :to="cap.link" class="text-link"
            >{{ cap.status }}<UIcon name="i-lucide-arrow-up-right" /></NuxtLink
          ><span v-else class="subtle-badge">{{ cap.status }}</span>
        </div>
      </div>
    </section>
    <section class="panel mt-6">
      <div class="panel-heading"><h2>Before the road opens.</h2></div>
      <div class="section-body !pt-0">
        <div
          v-for="gate in [
            'Server-side permission enforcement',
            'Cross-tenant resource isolation',
            'Secure identity for realtime connections',
            'Safe, audited device-command authorization',
          ]"
          :key="gate"
          class="settings-row"
        >
          <div class="flex items-center gap-3">
            <UIcon name="i-lucide-lock-keyhole" class="text-warning" />
            <h3 class="!mb-0">{{ gate }}</h3>
          </div>
          <span class="subtle-badge">Requires verification</span>
        </div>
      </div>
    </section>
  </div>
</template>
