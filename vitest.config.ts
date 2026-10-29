import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    include: ['tests/unit/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      include: [
        'app/features/fleet/domain/**/*.ts',
        'app/features/fleet/api/**/*.ts',
        'app/features/alerts/api/**/*.ts',
        'app/features/trips/api/**/*.ts',
        'app/features/geofences/api/**/*.ts',
        'app/features/settings/api/**/*.ts',
        'app/features/subscriptions/api/**/*.ts',
        'app/features/core/api/**/*.ts',
        'app/features/core/domain/**/*.ts',
        'app/features/core/services/**/*.ts',
        'app/features/auth/domain/**/*.ts',
        'app/features/auth/api/local-accounts-client.ts',
        'app/policies/**/*.ts',
        'app/features/workspace/api/preview-service.ts',
        'app/features/workspace/domain/trip-export.ts',
        'app/features/geofences/domain/**/*.ts',
        'app/features/tracking/domain/**/*.ts',
        'app/features/tracking/api/preview-tracks.ts',
        'app/features/maps/domain/**/*.ts',
        'app/features/maps/api/preview-search.ts',
        'app/features/maps/composables/useMapSearch.ts',
        'server/utils/wuu-gateway.ts',
        'server/utils/wuu-auth.ts',
        'server/utils/wuu-local-accounts.ts',
      ],
      thresholds: { lines: 80, functions: 80, statements: 80, branches: 80 },
    },
  },
})
