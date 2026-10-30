import { describe, expect, it } from 'vitest'
import {
  fullStartOptions,
  startupReceipt,
  startupGuard,
  assertGroupsStopped,
} from '../../tools/full-start-policy.mjs'
import { execFileSync, spawn } from 'node:child_process'
import { once } from 'node:events'
import { terminateProcessGroups } from '../../tools/fixture-lifecycle.mjs'
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'

describe('full-stack gate contract', () => {
  it('prevents resource creation synchronously after interruption', () => {
    const guard = startupGuard()
    guard.check()
    guard.abort()
    expect(() => guard.check()).toThrow('Startup interrupted')
  })

  it('rejects a surviving old process group before restart readiness can pass', async () => {
    const child = spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], {
      detached: true,
      stdio: 'ignore',
    })
    await once(child, 'spawn')
    try {
      expect(() => assertGroupsStopped([child])).toThrow('survived shutdown')
    } finally {
      await terminateProcessGroups(new Set([child]), { graceMs: 10 })
    }
    expect(() => assertGroupsStopped([child])).not.toThrow()
  })
  it('invalidates a stale PASS receipt when actual CLI preflight fails', () => {
    const directory = mkdtempSync('/tmp/opencode/full-start-policy-')
    try {
      writeFileSync(resolve(directory, 'last-startup.json'), '{"status":"PASS"}')
      expect(() =>
        execFileSync(process.execPath, ['tools/verify-java-integration.mjs', '--full-start'], {
          cwd: process.cwd(),
          env: { PATH: process.env.PATH, WUU_VERIFICATION_DIRECTORY: directory },
          stdio: 'pipe',
          timeout: 10000,
        }),
      ).toThrow()
      expect(JSON.parse(readFileSync(resolve(directory, 'last-startup.json'), 'utf8')).status).toBe(
        'FAIL',
      )
    } finally {
      rmSync(directory, { recursive: true, force: true })
    }
  })
  it('requires explicit backend and independent device artifacts for full acceptance', () => {
    expect(() => fullStartOptions(true, {})).toThrow()
    expect(() => fullStartOptions(true, { WUU_JAVA_JAR: '/backend.jar' })).toThrow()
    expect(
      fullStartOptions(true, { WUU_JAVA_JAR: '/backend.jar', WUU_DEVICE_JAR: '/device.jar' }),
    ).toMatchObject({ full: true, deviceJar: '/device.jar' })
    expect(fullStartOptions(false, {})).toMatchObject({ full: false })
  })

  it('cannot issue PASS unless startup, actual browser, restart and cleanup all passed', () => {
    const stages = [
      'dependencies',
      'backend',
      'device',
      'frontend',
      'browser',
      'restart',
      'persistence',
      'cleanup',
    ]
    for (const missing of stages)
      expect(
        startupReceipt({
          full: true,
          passed: stages.filter((item) => item !== missing),
          revisionsStable: true,
        }).status,
      ).toBe('FAIL')
    expect(startupReceipt({ full: true, passed: stages, revisionsStable: false }).status).toBe(
      'FAIL',
    )
    expect(startupReceipt({ full: true, passed: stages, revisionsStable: true }).status).toBe(
      'PASS',
    )
    expect(startupReceipt({ full: false, passed: stages, revisionsStable: true }).status).toBe(
      'PARTIAL',
    )
  })
})
