<script setup lang="ts">
import { geofenceRuleInputSchema, type GeofenceRuleInput } from '../domain/geofence-rule'
import type { Geofence } from '../../workspace/domain/types'
import { useGeofenceRules } from '../composables/useGeofenceRules'
import { useWorkspace } from '../../workspace/composables/useWorkspace'
import { errorMessage } from '../../core/domain/errors'

const props = defineProps<{ zone: Geofence }>()
const {
  rules,
  canRead,
  canWrite,
  accessError,
  retryAccess,
  busy,
  failure,
  success,
  create,
  setEnabled,
} = useGeofenceRules(() => props.zone.id)
const { vehicles, fleetQuery } = useWorkspace('geofences')
const open = ref(false)
const state = reactive<GeofenceRuleInput>({
  ruleKey: '',
  ruleName: '',
  geofenceId: props.zone.id,
  action: 'BOTH',
  vehicleIds: [],
  priority: 2,
  enabled: true,
})
const linked = computed(() =>
  vehicles.value.filter((vehicle) => props.zone.vehicleIds.includes(vehicle.id)),
)
const missingLinks = computed(() =>
  props.zone.vehicleIds.some((id) => !linked.value.some((vehicle) => vehicle.id === id)),
)
const createReady = computed(
  () =>
    canWrite.value &&
    props.zone.active &&
    props.zone.vehicleIds.length > 0 &&
    !fleetQuery.isPending.value &&
    !fleetQuery.isError.value &&
    !missingLinks.value,
)
watch(open, (value) => {
  if (value) {
    Object.assign(state, {
      ruleKey: '',
      ruleName: '',
      geofenceId: props.zone.id,
      action: 'BOTH',
      vehicleIds: [...props.zone.vehicleIds],
      priority: 2,
      enabled: true,
    })
    failure.value = ''
  }
})
async function submit() {
  if (!createReady.value) return
  const parsed = geofenceRuleInputSchema.safeParse(state)
  if (parsed.success && (await create(parsed.data))) open.value = false
}
</script>

<template>
  <section class="panel mt-6" aria-label="Geofence alert rules">
    <div class="panel-heading">
      <h2>Alert rules</h2>
      <UButton
        v-if="canWrite"
        :disabled="!createReady || busy"
        size="sm"
        icon="i-lucide-plus"
        @click="open = true"
        >Add geofence rule</UButton
      >
    </div>
    <div class="section-body detail-stack">
      <p v-if="accessError" role="alert">
        Rule permissions could not be checked.
        <UButton variant="link" @click="retryAccess">Retry rule permissions</UButton>
      </p>
      <p v-else-if="!canRead" class="muted">Rule records require alert-read permission.</p>
      <p v-else-if="rules.isPending.value" role="status">Loading rules…</p>
      <p v-else-if="rules.isError.value" role="alert">
        {{ errorMessage(rules.error.value, 'Rules could not load.') }}
        <UButton variant="link" @click="rules.refetch()">Retry rules</UButton>
      </p>
      <template v-else>
        <article
          v-for="rule in rules.data.value"
          :key="rule.ruleKey"
          class="detail-stack"
          :aria-label="`Rule ${rule.ruleKey}`"
        >
          <h3>{{ rule.ruleName }}</h3>
          <p>
            {{ rule.ruleKey }} · {{ rule.parameters.action }} · Priority {{ rule.priority }} ·
            {{ rule.enabled ? 'Enabled' : 'Disabled' }}
          </p>
          <p class="muted">Applies to {{ rule.parameters.vehicleIds.join(', ') }}</p>
          <p
            v-if="
              !zone.active || rule.parameters.vehicleIds.some((id) => !zone.vehicleIds.includes(id))
            "
            class="form-hint"
          >
            Inactive or unlinked boundaries do not apply to the affected vehicles.
          </p>
          <UButton
            v-if="canWrite"
            :disabled="busy || (!rule.enabled && !zone.active)"
            color="neutral"
            variant="outline"
            size="sm"
            @click="setEnabled(rule.ruleKey, !rule.enabled)"
            >{{ rule.enabled ? 'Disable' : 'Enable' }} {{ rule.ruleName }}</UButton
          >
        </article>
        <p v-if="!rules.data.value?.length" class="muted">
          No alert rules are configured for this boundary.
        </p>
      </template>
      <p v-if="!zone.active" class="form-hint">
        Enable the boundary before creating or enabling a rule.
      </p>
      <p v-else-if="!zone.vehicleIds.length" class="form-hint">
        Link at least one vehicle before configuring a rule.
      </p>
      <p v-else-if="canWrite && !createReady" class="form-hint">
        Complete fleet assignment reads are required before creating a rule.
      </p>
      <p v-if="failure && !open" role="alert" class="form-error">{{ failure }}</p>
      <p v-if="success" role="status">{{ success }}</p>
      <p class="form-hint">
        Entry/exit rules evaluate received vehicle locations. The existing five-minute cooldown
        applies per rule and vehicle, including opposite-direction transitions. Configuration does
        not prove device connectivity or notification delivery.
      </p>
    </div>
    <UModal
      v-model:open="open"
      title="Configure geofence rule"
      description="Create a supported entry/exit rule for the linked vehicles."
      :dismissible="!busy"
      :close="!busy"
    >
      <template #body>
        <UForm
          id="geofence-rule"
          :schema="geofenceRuleInputSchema"
          :state="state"
          class="form-stack"
          @submit="submit"
        >
          <UFormField label="Rule key" name="ruleKey" required
            ><UInput v-model="state.ruleKey" maxlength="100" :disabled="busy" class="w-full"
          /></UFormField>
          <p class="form-hint">Use a unique key: 3–100 letters, numbers, underscores or hyphens.</p>
          <UFormField label="Rule name" name="ruleName" required
            ><UInput v-model="state.ruleName" maxlength="200" :disabled="busy" class="w-full"
          /></UFormField>
          <UFormField label="Trigger" name="action" required
            ><USelect
              v-model="state.action"
              :items="['ENTRY', 'EXIT', 'BOTH']"
              :disabled="busy"
              class="w-full"
          /></UFormField>
          <UFormField label="Priority" name="priority" required
            ><UInput
              v-model.number="state.priority"
              type="number"
              min="1"
              max="100"
              :disabled="busy"
              class="w-full"
          /></UFormField>
          <UFormField label="Vehicles" name="vehicleIds" required>
            <p class="form-hint">Select 1–100 linked vehicles.</p>
            <label v-for="vehicle in linked" :key="vehicle.id" class="flex items-center gap-2"
              ><input
                v-model="state.vehicleIds"
                type="checkbox"
                :value="vehicle.id"
                :disabled="busy"
                :aria-label="`Apply rule to ${vehicle.name}`"
              />{{ vehicle.name }}</label
            >
          </UFormField>
          <USwitch v-model="state.enabled" label="Enable rule" :disabled="busy" />
          <p v-if="failure" role="alert" class="form-error">{{ failure }}</p>
        </UForm>
      </template>
      <template #footer
        ><div class="modal-footer">
          <UButton color="neutral" variant="outline" :disabled="busy" @click="open = false"
            >Cancel</UButton
          >
          <UButton type="submit" form="geofence-rule" :loading="busy" :disabled="!createReady"
            >Save rule</UButton
          >
        </div></template
      >
    </UModal>
  </section>
</template>
