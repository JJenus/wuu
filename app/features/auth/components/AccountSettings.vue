<script setup lang="ts">
import { useAuth } from '../composables/useAuth'
import { selfProfileSchema, passwordChangeFormSchema } from '#shared/self-account'

const auth = useAuth()
const hydrated = ref(false)
const blocked = computed(() => !hydrated.value || auth.busy.value)
onMounted(() => {
  hydrated.value = true
})
const profile = reactive({
  firstName: auth.user.value?.first_name ?? '',
  lastName: auth.user.value?.last_name ?? '',
})
const saved = ref({ ...profile })
const profileFailure = ref(''),
  profileNotice = ref(''),
  passwordFailure = ref('')
const passwords = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const dirty = computed(
  () => profile.firstName !== saved.value.firstName || profile.lastName !== saved.value.lastName,
)
watch(auth.user, (user) => {
  if (user && !dirty.value && !auth.busy.value) {
    Object.assign(profile, { firstName: user.first_name ?? '', lastName: user.last_name ?? '' })
    saved.value = { ...profile }
  }
})
watch(profile, () => {
  if (dirty.value) profileNotice.value = ''
})
async function saveProfile() {
  if (blocked.value) return
  profileFailure.value = ''
  profileNotice.value = ''
  if (!(await auth.updateProfile(selfProfileSchema.parse(profile)))) {
    profileFailure.value = auth.error.value
    return
  }
  Object.assign(profile, {
    firstName: auth.user.value?.first_name ?? '',
    lastName: auth.user.value?.last_name ?? '',
  })
  saved.value = { ...profile }
  profileNotice.value = 'Profile saved.'
}
function clearPasswords() {
  passwords.oldPassword = ''
  passwords.newPassword = ''
  passwords.confirmPassword = ''
}
async function savePassword() {
  if (blocked.value) return
  passwordFailure.value = ''
  try {
    if (
      !(await auth.changePassword({
        oldPassword: passwords.oldPassword,
        newPassword: passwords.newPassword,
      }))
    )
      passwordFailure.value = auth.error.value
  } finally {
    clearPasswords()
  }
}
onBeforeUnmount(clearPasswords)
</script>

<template>
  <div v-if="auth.user.value" class="content-grid mb-6">
    <section class="panel" aria-label="Your profile">
      <div class="panel-heading"><h2>Your profile</h2></div>
      <div class="section-body">
        <p class="section-description mb-4">
          {{ auth.user.value.email }} · Email is read-only here.
        </p>
        <UForm
          :state="profile"
          :schema="selfProfileSchema"
          class="form-stack"
          @submit="saveProfile"
        >
          <UFormField label="First name" name="firstName"
            ><UInput
              v-model="profile.firstName"
              autocomplete="given-name"
              :maxlength="100"
              :disabled="blocked"
              class="w-full"
          /></UFormField>
          <UFormField label="Last name" name="lastName"
            ><UInput
              v-model="profile.lastName"
              autocomplete="family-name"
              :maxlength="100"
              :disabled="blocked"
              class="w-full"
          /></UFormField>
          <p class="form-hint">
            These names belong to your account. Blank fields clear the saved names.
          </p>
          <p v-if="profileFailure" role="alert" class="form-error">{{ profileFailure }}</p>
          <p v-if="profileNotice" role="status">{{ profileNotice }}</p>
          <UButton type="submit" :loading="auth.busy.value" :disabled="!dirty || blocked"
            >Save profile</UButton
          >
        </UForm>
      </div>
    </section>
    <section class="panel" aria-label="Change password">
      <div class="panel-heading"><h2>Change password</h2></div>
      <div class="section-body">
        <p class="section-description mb-4">
          Changing your password ends all account sessions, including this one. You will need to
          sign in again.
        </p>
        <UForm
          :state="passwords"
          :schema="passwordChangeFormSchema"
          class="form-stack"
          @submit="savePassword"
        >
          <UFormField label="Current password" name="oldPassword" required
            ><UInput
              v-model="passwords.oldPassword"
              type="password"
              autocomplete="current-password"
              :maxlength="128"
              :disabled="blocked"
              class="w-full"
          /></UFormField>
          <UFormField label="New password" name="newPassword" required
            ><UInput
              v-model="passwords.newPassword"
              type="password"
              autocomplete="new-password"
              :maxlength="72"
              :disabled="blocked"
              class="w-full"
          /></UFormField>
          <UFormField label="Confirm new password" name="confirmPassword" required
            ><UInput
              v-model="passwords.confirmPassword"
              type="password"
              autocomplete="new-password"
              :maxlength="72"
              :disabled="blocked"
              class="w-full"
          /></UFormField>
          <p class="form-hint">
            At least 8 characters, at most 72 UTF-8 bytes. Spaces are preserved. Passwords are never
            saved in browser storage.
          </p>
          <p v-if="passwordFailure" role="alert" class="form-error">{{ passwordFailure }}</p>
          <UButton type="submit" :loading="auth.busy.value" :disabled="blocked"
            >Change password</UButton
          >
        </UForm>
      </div>
    </section>
  </div>
</template>
