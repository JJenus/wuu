import { execFileSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { resolve, join } from 'node:path'
import { describe, expect, it } from 'vitest'

const checker = resolve('tools/check-boundaries.mjs')

function check(path: string, source: string) {
  const root = mkdtempSync('/tmp/opencode/api-boundary-')
  try {
    mkdirSync(join(root, 'app'), { recursive: true })
    mkdirSync(join(root, 'server'), { recursive: true })
    mkdirSync(resolve(root, path, '..'), { recursive: true })
    writeFileSync(join(root, path), source)
    return execFileSync(process.execPath, [checker], { cwd: root, encoding: 'utf8', stdio: 'pipe' })
  } finally {
    // Remove only this test's freshly created, uniquely named fixture directory.
    rmSync(root, { recursive: true, force: true })
  }
}

describe('API boundary enforcement', () => {
  it.each([
    ['app/pages/test.vue', '<script setup>fetch("/api/_wuu/vehicles")</script>'],
    [
      'app/features/fleet/composables/useTest.ts',
      'const load = () => $fetch("/api/_wuu/vehicles")',
    ],
    ['app/pages/test.vue', 'import { service } from "../api/service"'],
    ['app/features/fleet/api/service.ts', 'const path = "/api/v1/vehicles"'],
    [
      'app/features/fleet/api/service.ts',
      'import { upstreamApi } from "../../../../server/utils/upstream-api"',
    ],
    ['server/utils/policy.ts', 'const path = "/api/v1/vehicles"'],
  ])('rejects API ownership violations in %s', (path, source) => {
    expect(() => check(path, source)).toThrow()
  })

  it.each([
    [
      'app/features/fleet/api/service.ts',
      'const load = () => client.request("GET", "/vehicles", schema)',
    ],
    ['server/utils/policy.ts', 'import { upstreamApi } from "./upstream-api"'],
    ['server/utils/upstream-api.ts', 'const base = "/api/v1"'],
    ['app/pages/test.vue', 'const workspace = useWorkspace("fleet")'],
  ])('admits the intended boundary in %s', (path, source) => {
    expect(check(path, source)).toContain('PASS')
  })
})
