<script setup lang="ts">
import { useAuth } from '~/features/auth/composables/useAuth'
import { loginSchema } from '~/features/auth/domain/auth'
definePageMeta({ layout: false })
useHead({ title: 'Sign in — Wuu' })
const source = useRuntimeConfig().public.dataMode
const route = useRoute()
const { busy, error, sessionError, signIn } = useAuth()
const state = reactive({ email: '', password: '' })
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
