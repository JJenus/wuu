import { describe, expect, it } from 'vitest'
import { fullStartOptions, startupReceipt } from '../../tools/full-start-policy.mjs'

describe('full-stack gate contract', () => {
  it('requires explicit backend and independent device artifacts for full acceptance', () => {
    expect(() => fullStartOptions(true, {})).toThrow()
    expect(() => fullStartOptions(true, { WUU_JAVA_JAR: '/backend.jar' })).toThrow()
    expect(fullStartOptions(true, { WUU_JAVA_JAR: '/backend.jar', WUU_DEVICE_JAR: '/device.jar' })).toMatchObject({ full: true, deviceJar: '/device.jar' })
    expect(fullStartOptions(false, {})).toMatchObject({ full: false })
  })

  it('cannot issue PASS unless startup, actual browser, restart and cleanup all passed', () => {
    const stages = ['dependencies', 'backend', 'device', 'frontend', 'browser', 'restart', 'persistence', 'cleanup']
    for (const missing of stages)
      expect(startupReceipt({ full: true, passed: stages.filter((item) => item !== missing), revisionsStable: true }).status).toBe('FAIL')
    expect(startupReceipt({ full: true, passed: stages, revisionsStable: false }).status).toBe('FAIL')
    expect(startupReceipt({ full: true, passed: stages, revisionsStable: true }).status).toBe('PASS')
    expect(startupReceipt({ full: false, passed: stages, revisionsStable: true }).status).toBe('PARTIAL')
  })
})
