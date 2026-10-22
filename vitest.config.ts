import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    include: ['tests/unit/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      include: [
        'app/features/fleet/domain/**/*.ts',
        'app/policies/**/*.ts',
        'app/features/workspace/api/preview-service.ts',
        'app/features/workspace/domain/trip-export.ts',
        'app/features/geofences/domain/**/*.ts',
        'app/features/tracking/domain/**/*.ts',
        'app/features/tracking/api/preview-tracks.ts',
        'app/features/maps/domain/**/*.ts',
        'app/features/maps/api/preview-search.ts',
        'app/features/maps/composables/useMapSearch.ts',
      ],
      thresholds: { lines: 80, functions: 80, statements: 80, branches: 80 },
    },
  },
})
