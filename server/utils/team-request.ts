import { z } from 'zod'
import { teamCreateSchema, teamProfileSchema, teamRolesSchema } from '../../shared/team'
import { upstreamApi, upstreamSuffix } from './upstream-api'
export function teamRequestAllowed(method: string, target: string): boolean {
  const suffix = upstreamSuffix(target, upstreamApi.team)
  if (suffix === '') return method === 'GET' || method === 'POST'
  if (suffix && /^\/[1-9][0-9]{0,15}$/.test(suffix))
    return ['GET', 'PUT', 'DELETE'].includes(method)
  return method === 'PUT' && !!suffix && /^\/[1-9][0-9]{0,15}\/roles$/.test(suffix)
}
export function teamQueryAllowed(target: string, query: unknown): boolean {
  const page = z
    .string()
    .regex(/^(0|[1-9][0-9]{0,5})$/)
    .optional()
  const size = z
    .string()
    .regex(/^[1-9][0-9]{0,2}$/)
    .refine((value) => Number(value) <= 100)
    .optional()
  return (
    target === upstreamApi.team ? z.object({ page, size }).strict() : z.object({}).strict()
  ).safeParse(query).success
}
export function teamRequestBody(method: string, target: string, body: unknown) {
  if (!teamRequestAllowed(method, target) || method === 'GET')
    throw new Error('Unsupported team operation')
  if (method === 'DELETE') return z.object({}).strict().parse(body)
  if (target.endsWith('/roles')) return { role_ids: teamRolesSchema.parse(body).roleIds }
  if (method === 'POST') {
    const input = teamCreateSchema.parse(body)
    return {
      email: input.email,
      password: input.password,
      first_name: input.firstName,
      last_name: input.lastName,
    }
  }
  const input = teamProfileSchema.parse(body)
  return { email: input.email, first_name: input.firstName, last_name: input.lastName }
}
