import { z } from 'zod'

export const localAccountSchema = z.object({
  email: z.email().max(255),
  display_name: z.string().min(1).max(255),
})
export type LocalAccount = z.infer<typeof localAccountSchema>
export const localAccountsSchema = z.object({
  enabled: z.boolean(),
  accounts: z.array(localAccountSchema).max(5000),
})
