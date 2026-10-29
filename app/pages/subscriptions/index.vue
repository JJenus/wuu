<script setup lang="ts">
import { useSubscriptions } from '~/features/subscriptions/composables/useSubscriptions'
const { live, catalog, owned, message, failure, busy, refreshing, enroll, cancel, refresh } =
  useSubscriptions()
const selectedCancellation = ref<string | null>(null)
const lastChecked = computed(() =>
  owned.dataUpdatedAt.value ? new Date(owned.dataUpdatedAt.value).toLocaleString() : 'not yet',
)
useHead({ title: 'Subscriptions — Wuu' })
</script>

<template>
  <div>
    <UiPageHeading
      title="Subscriptions"
      eyebrow="PLANS & ACCESS"
      description="View configured plans and your own enrollments."
    />
    <div class="info-banner">
      <UIcon name="i-lucide-info" />
      <p>
        Enrollment does not charge money or grant access. Trial and complimentary access require an
        explicit authorized grant. Paid activation and automatic renewal are not available.
      </p>
    </div>
    <UiEmptyState
      v-if="!live"
      title="Subscriptions require a live workspace"
      description="No billing or entitlement is simulated in preview. Sign in to a configured live workspace to view plans and enrollments."
    />
    <template v-else>
      <p v-if="message" role="status" class="text-success mb-4">{{ message }}</p>
      <p v-if="failure" role="alert" class="text-error mb-4">{{ failure }}</p>
      <h2 class="text-xl font-semibold text-highlighted mb-4">Configured plans</h2>
      <UiAsyncState
        :pending="catalog.isPending.value"
        :error="catalog.error.value"
        loading-label="Loading plans…"
        error-title="Plans could not load"
        @retry="catalog.refetch()"
      >
        <div class="grid gap-4 md:grid-cols-2 mb-8">
          <section
            v-for="plan in catalog.data.value"
            :key="`${plan.planId}:${plan.version}`"
            class="panel p-6"
          >
            <h3 class="font-semibold text-highlighted">
              {{
                plan.model === 'USAGE_TOKEN'
                  ? 'Usage tokens'
                  : plan.cadence === 'MONTHLY'
                    ? 'Monthly plan'
                    : 'Yearly plan'
              }}
            </h3>
            <p class="text-muted break-all">{{ plan.planId }} · Version {{ plan.version }}</p>
            <p class="mt-3">{{ plan.usageLimit }} {{ plan.meter }} units</p>
            <p class="text-muted">
              Indicative configured price: {{ plan.price.amount }} {{ plan.price.currency }}
            </p>
            <p class="text-muted text-sm mt-2">
              Display may round decimal prices. No charge, renewal, or access is implied.
            </p>
            <UButton class="mt-4" :disabled="busy" @click="enroll(plan.planId, plan.version)"
              >Request pending enrollment</UButton
            >
          </section>
        </div>
        <UiEmptyState
          v-if="catalog.data.value?.length === 0"
          title="No plans configured"
          description="An authorized administrator must configure the catalog before enrollment is possible."
        />
      </UiAsyncState>
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 class="text-xl font-semibold text-highlighted">Your enrollments</h2>
        <UButton
          variant="outline"
          color="neutral"
          icon="i-lucide-refresh-cw"
          :loading="refreshing"
          @click="refresh"
          >Refresh subscriptions</UButton
        >
      </div>
      <p class="text-muted text-sm mb-4">
        Last-checked snapshot: {{ lastChecked }}. Values can change when an administrator grants
        units or a grant expires; access is always rechecked by the server.
      </p>
      <UiAsyncState
        :pending="owned.isPending.value"
        :error="owned.error.value"
        loading-label="Loading enrollments…"
        error-title="Enrollments could not load"
        @retry="owned.refetch()"
      >
        <div class="grid gap-4">
          <section v-for="item in owned.data.value" :key="item.id" class="panel p-6">
            <h3 class="font-semibold text-highlighted break-all">Enrollment {{ item.id }}</h3>
            <p class="text-muted break-all">Plan {{ item.planId }} · Version {{ item.version }}</p>
            <p class="mt-2">Status: {{ item.status }}</p>
            <p>Spendable units: {{ item.remainingUnits }}</p>
            <p v-if="item.status === 'PENDING'" class="text-muted">
              Pending configuration only. No payment or access grant.
            </p>
            <template v-if="item.status !== 'CANCELED'">
              <div v-if="selectedCancellation === item.id" class="mt-4">
                <p>
                  Cancel this enrollment? Any remaining granted units will no longer be available.
                </p>
                <UButton
                  class="mt-2"
                  color="error"
                  :disabled="busy"
                  @click="
                    cancel(item.id).then((ok) => {
                      if (ok) selectedCancellation = null
                    })
                  "
                  >Confirm cancellation</UButton
                >
                <UButton
                  class="ml-2"
                  variant="outline"
                  color="neutral"
                  :disabled="busy"
                  @click="selectedCancellation = null"
                  >Keep enrollment</UButton
                >
              </div>
              <UButton
                v-else
                class="mt-4"
                variant="outline"
                color="neutral"
                :disabled="busy"
                @click="selectedCancellation = item.id"
                >Cancel enrollment</UButton
              >
            </template>
          </section>
        </div>
        <UiEmptyState
          v-if="owned.data.value?.length === 0"
          title="No enrollments"
          description="Choose a configured plan to record a pending enrollment. This will not activate access or charge money."
        />
      </UiAsyncState>
    </template>
  </div>
</template>
