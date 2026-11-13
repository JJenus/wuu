import { z } from 'zod'

export const selfProfileSchema = z
  .object({
    firstName: z.string().max(100).trim(),
    lastName: z.string().max(100).trim(),
  })
  .strict()

// Passwords are never trimmed; Java enforces the same codepoint/UTF-8 byte policy.
export const passwordChangeSchema = z
  .object({
    oldPassword: z
      .string()
      .min(1, 'Enter your current password.')
      .max(128)
      .refine((value) => value.trim().length > 0, 'Enter your current password.'),
    newPassword: z
      .string()
      .refine(
        (value) => value.trim().length > 0 && Array.from(value).length >= 8,
        'Use at least 8 characters.',
      )
      .refine(
        (value) => new TextEncoder().encode(value).length <= 72,
        'Use at most 72 UTF-8 bytes.',
      ),
  })
  .strict()
export const passwordChangeFormSchema = passwordChangeSchema
  .extend({ confirmPassword: z.string() })
  .refine((value) => value.newPassword === value.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match.',
  })
export type SelfProfileInput = z.infer<typeof selfProfileSchema>
export type PasswordChangeInput = z.infer<typeof passwordChangeSchema>
