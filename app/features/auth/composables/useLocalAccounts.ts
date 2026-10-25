import { localAccountsRequest } from '../api/local-accounts-client'
import type { LocalAccount } from '../domain/local-accounts'
import { errorMessage } from '../../core/domain/errors'

export function useLocalAccounts() {
  const enabled = ref(false)
  const accounts = ref<LocalAccount[]>([])
  const loading = ref(false)
  const error = ref('')
  const live = useRuntimeConfig().public.dataMode === 'live'
  async function refresh() {
    if (!live || loading.value) return
    loading.value = true
    error.value = ''
    accounts.value = []
    try {
      const result = await localAccountsRequest()
      enabled.value = result.enabled
      accounts.value = result.accounts
    } catch (failure) {
      error.value = errorMessage(failure)
    } finally {
      loading.value = false
    }
  }
  onMounted(() => {
    void refresh()
    window.addEventListener('focus', refresh)
  })
  onUnmounted(() => window.removeEventListener('focus', refresh))
  return { enabled, accounts, loading, error, refresh }
}
