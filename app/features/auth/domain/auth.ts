import { z } from 'zod'

export const loginSchema = z.object({
  email: z.string().trim().email().max(255),
  password: z.string().min(1, 'Enter your password.').max(128),
})

// Roles are descriptive; no client-side role-to-permission expansion is performed.
export const userSchema = z.object({
  id: z.number().int().positive().safe(),
  email: z.string().email(),
  first_name: z.string().nullable(),
  last_name: z.string().nullable(),
  organization_id: z.number().int().positive().safe().nullable(),
  roles: z.array(z.string()),
  enabled: z.boolean(),
  locked: z.boolean(),
})
export type AuthUser = z.infer<typeof userSchema>

export function safeReturnPath(value: unknown): string {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    /[\\%\u0000-\u0020]/.test(value) ||
    value.startsWith('//')
  )
    return '/'
  const path = value.split(/[?#]/)[0] ?? '/'
  if (
    path === '/sign-in' ||
    path.startsWith('/api/') ||
    path.startsWith('/admin') ||
    path === '/access-denied'
  )
    return '/'
  return value
}
