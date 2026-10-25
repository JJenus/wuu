<script setup lang="ts">
import { useAuth } from '~/features/auth/composables/useAuth'
import { loginSchema } from '~/features/auth/domain/auth'
import { useLocalAccounts } from '~/features/auth/composables/useLocalAccounts'
definePageMeta({ layout: false })
useHead({ title: 'Sign in — Wuu' })
const source = useRuntimeConfig().public.dataMode
const route = useRoute()
const { busy, error, sessionError, signIn } = useAuth()
const state = reactive({ email: '', password: '' })
const {
  enabled: pickerEnabled,
  accounts,
  loading: accountsLoading,
  error: accountsError,
  refresh,
} = useLocalAccounts()
async function selectAccount(email: string) {
  state.email = email
  state.password = ''
  error.value = ''
  await nextTick()
  document.getElementById('sign-in-password')?.focus()
}
async function submit() {
  await signIn(state.email.trim(), state.password, route.query.next)
  state.password = ''
}
</script>

<template>
  <main class="min-h-screen bg-muted flex items-center justify-center p-6">
    <section
      class="w-full max-w-md rounded-xl border border-default bg-default p-8 space-y-6"
      aria-labelledby="sign-in-title"
    >
      <div class="flex items-center justify-between">
        <NuxtLink to="/" class="flex items-center gap-3 text-xl font-semibold text-highlighted"
          ><img src="/brand/logo-96.png" alt="Wuu logo" width="44" height="44" />Wuu</NuxtLink
        ><UColorModeButton color="neutral" variant="ghost" />
      </div>
      <div>
        <h1 id="sign-in-title" class="text-2xl font-semibold text-highlighted">
          Your fleet, in focus.
        </h1>
        <p class="text-muted mt-2">Sign in to your Wuu workspace.</p>
      </div>
      <template v-if="source === 'live'">
        <p v-if="route.query.expired" role="status" class="text-muted">
          Your session expired. Sign in again to continue.
        </p>
        <p v-if="route.query.revocation" role="alert" class="text-warning">
          You are signed out of this browser. The service could not confirm revocation of your other
          sessions. Contact your administrator if needed.
        </p>
        <p v-if="sessionError" role="alert" class="text-error">{{ sessionError }}</p>
        <section
          v-if="pickerEnabled || accountsError"
          aria-labelledby="local-accounts-title"
          class="space-y-3"
        >
          <div class="flex items-center justify-between gap-3">
            <h2 id="local-accounts-title" class="font-semibold text-highlighted">
              Choose a local account
            </h2>
            <UButton
              color="neutral"
              variant="ghost"
              size="sm"
              :loading="accountsLoading"
              :disabled="busy"
              @click="refresh"
              >Refresh accounts</UButton
            >
          </div>
          <p class="text-sm text-muted">
            Local development only. Select an account, then enter its password.
          </p>
          <p v-if="accountsError" role="alert" class="text-sm text-error">{{ accountsError }}</p>
          <p v-else-if="accountsLoading" role="status" class="text-sm text-muted">
            Loading accounts…
          </p>
          <p v-else-if="!accounts.length" role="status" class="text-sm text-muted">
            No available accounts. You can still enter your email below.
          </p>
          <div v-else class="max-h-52 overflow-y-auto space-y-2" aria-label="Local accounts">
            <UButton
              v-for="account in accounts"
              :key="account.email"
              color="neutral"
              variant="outline"
              block
              class="justify-start text-left"
              :disabled="busy"
              :aria-pressed="state.email === account.email"
              @click="selectAccount(account.email)"
            >
              <span class="min-w-0"
                ><span class="block break-words">{{ account.display_name }}</span
                ><span class="block break-all text-xs text-muted">{{ account.email }}</span></span
              >
            </UButton>
          </div>
        </section>
        <UForm :schema="loginSchema" :state="state" class="space-y-5" @submit="submit">
          <UFormField label="Email" name="email" required
            ><UInput
              v-model="state.email"
              type="email"
              autocomplete="username"
              class="w-full"
              :disabled="busy"
          /></UFormField>
          <UFormField label="Password" name="password" required
            ><UInput
              v-model="state.password"
              id="sign-in-password"
              type="password"
              autocomplete="current-password"
              class="w-full"
              :disabled="busy"
          /></UFormField>
          <p v-if="error" role="alert" class="text-error">{{ error }}</p>
          <UButton type="submit" :loading="busy" block>Sign in</UButton>
        </UForm>
        <p class="text-sm text-muted">
          Sessions expire automatically. Sign-out ends all sessions for your account. Need access or
          a password reset? Contact your administrator.
        </p>
      </template>
      <template v-else
        ><p class="text-muted">
          This is the sample-data preview. No account or credentials are needed.
        </p>
        <UButton to="/" block>Explore the preview</UButton></template
      >
    </section>
  </main>
</template>
