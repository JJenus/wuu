export type DataMode = 'preview' | 'live'

export interface WorkspaceContext {
  mode: DataMode
  organizationId: string
  userId: string
}

export function isDataMode(value: unknown): value is DataMode {
  return value === 'preview' || value === 'live'
}

export function workspaceContext(input: {
  mode: string | undefined
  organizationId?: string | null
  userId?: string | null
}): WorkspaceContext {
  return {
    mode: isDataMode(input.mode) ? input.mode : 'preview',
    organizationId: input.organizationId || 'northstar',
    userId: input.userId || 'preview-user',
  }
}
