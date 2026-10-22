import type { WorkspaceContext } from '../../core/domain/data-mode'
import type { WorkspaceServices } from '../../core/services/workspace-services'

export function useWorkspaceServices(): WorkspaceServices {
  return useNuxtApp().$workspaceServices as WorkspaceServices
}

export function useWorkspaceContextValue(): WorkspaceContext {
  return useNuxtApp().$workspaceContext as WorkspaceContext
}
