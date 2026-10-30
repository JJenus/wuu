import { z } from 'zod'

/** Only individual alert transitions; bulk, rules and geofences stay unavailable. */
export function alertTransitionAllowed(method: string, target: string): boolean {
  if (method !== 'POST') return false
  const match = /^\/alerts\/([1-9][0-9]{0,18})\/(acknowledge|resolve)$/.exec(target)
  return !!match && BigInt(match[1]!) <= 9223372036854775807n
}

export function alertTransitionBody(target: string, body: unknown): Record<string, unknown> {
  if (!alertTransitionAllowed('POST', target)) throw new Error('Unknown alert transition')
  if (target.endsWith('/acknowledge')) return z.object({}).strict().parse(body)
  const input = z
    .object({ resolutionNotes: z.string().trim().min(10).max(1000) })
    .strict()
    .parse(body)
  // Production Java uses Jackson SNAKE_CASE; keep the browser contract local.
  return { resolution_notes: input.resolutionNotes }
}
