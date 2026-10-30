/** @param {boolean} full @param {Record<string, string | undefined>} env */
export function fullStartOptions(full, env) {
  if (full && (!env.WUU_JAVA_JAR || !env.WUU_DEVICE_JAR))
    throw new Error('Full startup requires explicit WUU_JAVA_JAR and WUU_DEVICE_JAR artifacts.')
  return { full, deviceJar: env.WUU_DEVICE_JAR }
}

/** @param {{full: boolean, passed: string[], revisionsStable: boolean}} result */
export function startupReceipt(result) {
  const required = [
    'dependencies',
    'backend',
    'device',
    'frontend',
    'browser',
    'restart',
    'persistence',
    'cleanup',
  ]
  const missing = required.filter((stage) => !result.passed.includes(stage))
  return {
    status: !result.full ? 'PARTIAL' : result.revisionsStable && !missing.length ? 'PASS' : 'FAIL',
    missing,
    revisionsStable: result.revisionsStable,
    passed: result.passed,
  }
}

export function startupGuard() {
  let aborted = false
  return {
    abort() {
      aborted = true
    },
    check() {
      if (aborted) throw new Error('Startup interrupted')
    },
  }
}

/** @param {Iterable<{pid?: number}>} children */
export function assertGroupsStopped(children) {
  for (const child of children) {
    if (!child.pid) continue
    try {
      process.kill(-child.pid, 0)
    } catch (error) {
      if (error.code === 'ESRCH') continue
      throw error
    }
    throw new Error('An owned process group survived shutdown')
  }
}
