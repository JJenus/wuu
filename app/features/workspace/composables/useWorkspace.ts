import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { isRetryable } from '../../core/domain/errors'
import { queryKeys } from '../../core/queries/keys'
import type { NewVehicle } from '../../fleet/domain/fleet'
import { fleetSummary } from '../../fleet/domain/fleet'
import type { GeofenceDraft } from '../../geofences/domain/geofence'
import type { AlertState } from '../domain/types'
import type { AlertTransitionContext } from '../../alerts/services/alert-service'
import { useWorkspaceContextValue, useWorkspaceServices } from './useWorkspaceServices'

export function useWorkspace(domain?: 'fleet' | 'alerts' | 'trips' | 'geofences') {
  const services = useWorkspaceServices()
  const context = useWorkspaceContextValue()
  const queryClient = useQueryClient()
  const isServer = import.meta.server

  const fleetQuery = useQuery({
    queryKey: queryKeys.fleet.list(context),
    queryFn: ({ signal }) => services.fleet.list({ signal }),
    enabled: services.mode === 'preview' || !isServer,
    staleTime: 30_000,
    retry: (count, error) => count < 1 && isRetryable(error),
  })
  const alertsQuery = useQuery({
    queryKey: queryKeys.alerts.list(context),
    queryFn: ({ signal }) => services.alerts.list({ signal }),
    enabled: services.mode === 'preview' || !isServer,
    staleTime: 15_000,
    retry: (count, error) => count < 1 && isRetryable(error),
  })
  const tripsQuery = useQuery({
    queryKey: queryKeys.trips.list(context),
    queryFn: ({ signal }) => services.trips.list({ signal }),
    enabled: services.mode === 'preview' || !isServer,
    staleTime: 30_000,
    retry: (count, error) => count < 1 && isRetryable(error),
  })
  const geofencesQuery = useQuery({
    queryKey: queryKeys.geofences.list(context),
    queryFn: ({ signal }) => services.geofences.list({ signal }),
    enabled: services.mode === 'preview' || !isServer,
    staleTime: 30_000,
    retry: (count, error) => count < 1 && isRetryable(error),
  })

  const vehicles = computed(() => fleetQuery.data.value ?? [])
  const alerts = computed(() => alertsQuery.data.value ?? [])
  const trips = computed(() => tripsQuery.data.value ?? [])
  const geofences = computed(() => geofencesQuery.data.value ?? [])
  const summary = computed(() => fleetSummary(vehicles.value))
  // An unrelated 403 must never hide resources the server did permit this user to read.
  const queries = {
    fleet: fleetQuery,
    alerts: alertsQuery,
    trips: tripsQuery,
    geofences: geofencesQuery,
  }
  const activeQueries = domain ? [queries[domain]] : Object.values(queries)
  const pendingStates = activeQueries.map((query) => query.isPending)
  const errorStates = activeQueries.map((query) => query.error)
  const isPending = computed(() => pendingStates.some((state) => state.value))
  const isFetching = computed(
    () =>
      fleetQuery.isFetching.value ||
      alertsQuery.isFetching.value ||
      tripsQuery.isFetching.value ||
      geofencesQuery.isFetching.value,
  )
  const isError = computed(() => errorStates.some((state) => Boolean(state.value)))
  const error = computed(() => errorStates.find((state) => Boolean(state.value))?.value ?? null)

  async function refetch() {
    await Promise.all([
      fleetQuery.refetch(),
      alertsQuery.refetch(),
      tripsQuery.refetch(),
      geofencesQuery.refetch(),
    ])
  }

  async function refresh() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.fleet.list(context) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.alerts.list(context) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.trips.list(context) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.geofences.list(context) }),
    ])
  }

  const addVehicle = useMutation({
    mutationFn: (input: NewVehicle) => services.fleet.create(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.fleet.list(context) }),
  })

  const updateAlert = useMutation({
    mutationFn: (input: { id: string; status: AlertState; context?: AlertTransitionContext }) =>
      services.alerts.transition(input.id, input.status, input.context),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.alerts.list(context) }),
  })

  const createGeofence = useMutation({
    mutationFn: (input: GeofenceDraft) => services.geofences.create(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.geofences.list(context) }),
  })

  const updateGeofence = useMutation({
    mutationFn: (input: { id: string; draft: GeofenceDraft }) =>
      services.geofences.update(input.id, input.draft),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.geofences.list(context) }),
  })

  return {
    services,
    context,
    vehicles,
    alerts,
    trips,
    geofences,
    summary,
    fleetQuery,
    alertsQuery,
    tripsQuery,
    geofencesQuery,
    isPending,
    isFetching,
    isError,
    error,
    refetch,
    refresh,
    addVehicle: (input: NewVehicle) => addVehicle.mutateAsync(input),
    isAddingVehicle: addVehicle.isPending,
    updateAlert: (id: string, status: AlertState, transitionContext?: AlertTransitionContext) =>
      updateAlert.mutateAsync({ id, status, context: transitionContext }),
    isUpdatingAlert: updateAlert.isPending,
    createGeofence: (input: GeofenceDraft) => createGeofence.mutateAsync(input),
    isCreatingGeofence: createGeofence.isPending,
    updateGeofence: (id: string, draft: GeofenceDraft) => updateGeofence.mutateAsync({ id, draft }),
    isUpdatingGeofence: updateGeofence.isPending,
  }
}
