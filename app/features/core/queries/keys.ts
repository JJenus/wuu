import type { DataMode, WorkspaceContext } from '../domain/data-mode'

export const queryKeys = {
  fleet: {
    all: (context: WorkspaceContext) => ['fleet', context] as const,
    list: (context: WorkspaceContext) => ['fleet', context, 'list'] as const,
    detail: (context: WorkspaceContext, id: string) => ['fleet', context, 'detail', id] as const,
  },
  alerts: {
    all: (context: WorkspaceContext) => ['alerts', context] as const,
    list: (context: WorkspaceContext) => ['alerts', context, 'list'] as const,
    detail: (context: WorkspaceContext, id: string) => ['alerts', context, 'detail', id] as const,
  },
  trips: {
    all: (context: WorkspaceContext) => ['trips', context] as const,
    list: (context: WorkspaceContext) => ['trips', context, 'list'] as const,
    detail: (context: WorkspaceContext, id: string) => ['trips', context, 'detail', id] as const,
  },
  geofences: {
    all: (context: WorkspaceContext) => ['geofences', context] as const,
    list: (context: WorkspaceContext) => ['geofences', context, 'list'] as const,
    detail: (context: WorkspaceContext, id: string) =>
      ['geofences', context, 'detail', id] as const,
  },
  preferences: {
    all: (context: WorkspaceContext) => ['preferences', context] as const,
    current: (context: WorkspaceContext) => ['preferences', context, 'current'] as const,
  },
  scope: (mode: DataMode, organizationId: string, userId: string) =>
    ['workspace', mode, organizationId, userId] as const,
}
