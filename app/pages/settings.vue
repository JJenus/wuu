<script setup lang="ts">
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
const { data, savePreferences } = useWorkspace()
const prefs = reactive({ email: true, push: true, weekly: false })
const busy = ref(false)
const toast = useToast()
watch(
  () => data.value?.preferences,
  (value) => {
    if (value) Object.assign(prefs, value)
  },
  { immediate: true },
)
async function save() {
  busy.value = true
  try {
    await savePreferences({ ...prefs })
    toast.add({
      title: 'Preview preferences saved',
      description: 'Your changes last for this browser session.',
      color: 'success',
      icon: 'i-lucide-check',
    })
  } catch {
    toast.add({ title: 'Could not save preferences', color: 'error' })
  } finally {
    busy.value = false
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
          <span>Preview only. No emails or push messages are sent.</span
          ><UButton :loading="busy" @click="save">Save preferences</UButton>
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
            <span class="preview-tag"><span />Preview</span>
          </div>
          <div class="section-body !pt-0">
            <p class="section-description">
              Wuu is a calmer place to manage the road ahead. You’re exploring sample vehicles,
              trips, and alerts for Northstar Logistics. No live backend is connected.
            </p>
            <p class="section-description mt-3">
              Vehicle additions, alert updates, and notification preferences stay in memory for this
              session and reset when the page reloads. No account or login is required for this
              preview.
            </p>
            <div class="flex items-center gap-2 mt-5 text-xs text-primary">
              <UIcon name="i-lucide-code-xml" />Nuxt 4 · Vue 3 · Nuxt UI 4
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>
