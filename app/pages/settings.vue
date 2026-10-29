<script setup lang="ts">
import { errorMessage } from '~/features/core/domain/errors'
import { useDataSource } from '~/features/core/composables/useDataSource'
import { usePreferences } from '~/features/workspace/composables/usePreferences'
import type { Preferences } from '~/features/workspace/domain/types'
import EffectiveAccessPanel from '~/features/auth/components/EffectiveAccessPanel.vue'

const source = useDataSource()
const { preferences, isPending, isError, error, refetch, savePreferences, isSaving } =
  usePreferences()
const prefs = reactive<Preferences>({ email: true, push: true, weekly: false })
const saved = ref<Preferences | null>(null)
const touched = ref(false)
const ready = ref(false)
const toast = useToast()

const dirty = computed(
  () =>
    !saved.value ||
    prefs.email !== saved.value.email ||
    prefs.push !== saved.value.push ||
    prefs.weekly !== saved.value.weekly,
)

watch(
  prefs,
  () => {
    if (ready.value) touched.value = true
  },
  { deep: true },
)

watch(
  preferences,
  (value) => {
    if (!value) return
    if (ready.value && touched.value) return
    Object.assign(prefs, value)
    saved.value = { ...value }
    ready.value = true
    touched.value = false
  },
  { immediate: true },
)

async function save() {
  if (isSaving.value || !dirty.value) return
  try {
    await savePreferences({ ...prefs })
    saved.value = { ...prefs }
    touched.value = false
    toast.add({
      title: source.isPreview ? 'Preview preferences saved' : 'Preferences saved',
      description: source.isPreview
        ? 'Your changes last for this browser session.'
        : 'Your notification choices have been stored.',
      color: 'success',
      icon: 'i-lucide-check',
    })
  } catch (saveError) {
    toast.add({
      title: 'Could not save preferences',
      description: errorMessage(saveError),
      color: 'error',
    })
  }
}
</script>

<template>
  <div>
    <UiPageHeading
      title="Make yourself at home."
      description="A workspace that works the way you do."
      eyebrow="WORKSPACE SETTINGS"
    />
    <EffectiveAccessPanel v-if="!source.isPreview" />
    <UiAsyncState
      :pending="isPending && !preferences"
      :error="isError ? error : null"
      loading-label="Loading your preferences…"
      error-title="Preferences are unavailable"
      @retry="refetch"
    >
      <div class="content-grid">
        <section class="panel">
          <div class="panel-heading">
            <div>
              <h2>Notifications, on your terms.</h2>
              <p>Choose what you’d like to hear about.</p>
            </div>
          </div>
          <div class="section-body !pt-0">
            <div class="settings-row">
              <div>
                <h3>Email alerts</h3>
                <p>Important fleet events, delivered to your inbox.</p>
              </div>
              <USwitch v-model="prefs.email" aria-label="Email alerts" />
            </div>
            <div class="settings-row">
              <div>
                <h3>Browser notifications</h3>
                <p>A heads-up while you’re in the workspace.</p>
              </div>
              <USwitch v-model="prefs.push" aria-label="Browser notifications" />
            </div>
            <div class="settings-row">
              <div>
                <h3>Weekly fleet summary</h3>
                <p>A simple recap of the week on the road.</p>
              </div>
              <USwitch v-model="prefs.weekly" aria-label="Weekly fleet summary" />
            </div>
          </div>
          <div class="settings-footer">
            <span v-if="source.isPreview">Preview only. No emails or push messages are sent.</span>
            <span v-else>Notification delivery is managed by your organization.</span>
            <UButton :loading="isSaving" :disabled="!dirty || isSaving" @click="save"
              >Save preferences</UButton
            >
          </div>
        </section>
        <div class="flex flex-col gap-5">
          <section class="panel">
            <div class="panel-heading"><h2>A little more you.</h2></div>
            <div class="section-body !pt-0">
              <div class="settings-row">
                <div>
                  <h3>Appearance</h3>
                  <p>Light or dark. Find your comfort zone.</p>
                </div>
                <UColorModeSelect class="w-32" />
              </div>
            </div>
          </section>
          <section class="panel">
            <div class="panel-heading">
              <h2>About this workspace</h2>
              <span class="preview-tag"><span />{{ source.isPreview ? 'Preview' : 'Live' }}</span>
            </div>
            <div class="section-body !pt-0">
              <template v-if="source.isPreview">
                <p class="section-description">
                  Wuu is a calmer place to manage the road ahead. You’re exploring sample vehicles,
                  trips, and alerts for Northstar Logistics. No live backend is connected.
                </p>
                <p class="section-description mt-3">
                  Vehicle additions, alert updates, and notification preferences stay in memory for
                  this session and reset when the page reloads. No account or login is required for
                  this preview.
                </p>
              </template>
              <p v-else class="section-description">
                This workspace uses the configured live Wuu service. This page never infers a
                permission from a client role, and server authorization still requires verification
                before production use.
              </p>
              <div class="flex items-center gap-2 mt-5 text-xs text-primary">
                <UIcon name="i-lucide-code-xml" />Nuxt 4 · Vue 3 · Nuxt UI 4
              </div>
            </div>
          </section>
        </div>
      </div>
    </UiAsyncState>
  </div>
</template>
