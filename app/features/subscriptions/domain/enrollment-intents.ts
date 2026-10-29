type IntentStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

/** Stores only random operation IDs, never credentials or billing/account payloads. */
export function createEnrollmentIntents(
  storage: IntentStorage,
  owner: string,
  generate: () => string,
) {
  const address = (plan: string) =>
    `wuu:enrollment-intent:${encodeURIComponent(owner)}:${encodeURIComponent(plan)}`
  return {
    key(plan: string) {
      const addressKey = address(plan)
      const existing = storage.getItem(addressKey)
      if (existing) return existing
      const key = generate()
      storage.setItem(addressKey, key)
      return key
    },
    confirm(plan: string) {
      storage.removeItem(address(plan))
    },
  }
}
