/** Adapt the backend's snake_case JSON while keeping domain models camelCase. */
export function normalizeWireKeys(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(normalizeWireKeys)
  if (!value || typeof value !== 'object') return value
  const result: Record<string, unknown> = Object.create(null)
  for (const [key, child] of Object.entries(value)) {
    const normalized = key.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase())
    if (Object.hasOwn(result, normalized)) throw new Error('Ambiguous service fields.')
    result[normalized] = normalizeWireKeys(child)
  }
  return result
}
