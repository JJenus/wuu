export interface TeamMember {
  id: string
  email: string
  firstName: string | null
  lastName: string | null
  enabled: boolean
  locked: boolean
  organizationId: number | null
  roles: string[]
  createdAt: string | null
}
